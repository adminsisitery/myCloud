package com.cloudrive.aiService.agent;

import com.cloudrive.aiService.agent.action.AgentActionService;
import com.cloudrive.aiService.agent.tool.AgentToolFactory;

import com.cloudrive.aiService.model.ModelRegistry;
import com.cloudrive.aiService.persistence.mapper.AiConversationMapper;
import com.cloudrive.aiService.chat.dto.ChatRequest;
import com.cloudrive.aiService.persistence.entity.AiConversation;
import com.cloudrive.pan_common.util.UserContext;
import dev.langchain4j.data.message.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.*;

@Component
public class AgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private final ModelRegistry models;
    private final AiConversationMapper conversations;
    private final AgentRunStore store;
    private final AgentProperties config;
    private final AgentEngine engine;
    private final AgentToolFactory tools;
    private final AgentActionService actions;
    private final MeterRegistry metrics;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService scheduler;
    private final Semaphore capacity;
    private final Map<String, Active> active = new ConcurrentHashMap<>();

    public AgentOrchestrator(ModelRegistry models, AiConversationMapper conversations, AgentRunStore store, AgentProperties config,
                             AgentEngine engine, AgentToolFactory tools, AgentActionService actions, MeterRegistry metrics) {
        this.models = models;
        this.conversations = conversations;
        this.store = store;
        this.config = config;
        this.engine = engine;
        this.tools = tools;
        this.actions = actions;
        this.metrics = metrics;
        capacity = new Semaphore(config.getMaxConcurrentRuns());
        workers = new ThreadPoolExecutor(config.getMaxConcurrentRuns(), config.getMaxConcurrentRuns(), 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), Thread.ofPlatform().daemon(true).name("agent-worker-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("agent-heartbeat").factory());
        scheduler.scheduleWithFixedDelay(this::heartbeat, 10, 10, TimeUnit.SECONDS);
        metrics.gauge("ai.agent.active", active, Map::size);
    }

    public SseEmitter execute(ChatRequest request) {
        return start(request, null);
    }

    /**
     * A continuation is a new durable attempt; a deterministic key prevents double resumes.
     */
    public SseEmitter resume(String id, String answer) {
        Long userId = UserContext.getCurrentUserId();
        AgentRun previous = store.get(id, userId);
        if (previous.getContinuationDepth() >= 10 || previous.getPriorTokens() + Math.max(previous.totalTokens(), previous.getBudgetTokens()) >= config.getMaxTotalTokens())
            throw new ResponseStatusException(CONFLICT, "连续任务预算已用完，请缩小任务范围后新建任务");
        if (!Set.of("WAITING_CONFIRMATION", "WAITING_INPUT", "FAILED", "CANCELLED", "TIMED_OUT").contains(previous.getStatus()))
            throw new ResponseStatusException(CONFLICT, "该任务无需继续");
        var decisions = actions.list(id, userId);
        if (decisions.stream().anyMatch(a -> Set.of("PENDING", "EXECUTING", "UNKNOWN").contains(a.getStatus())))
            throw new ResponseStatusException(CONFLICT, "请先处理待确认操作或核实未知结果");
        if (answer != null && answer.length() > 2000)
            throw new ResponseStatusException(BAD_REQUEST, "补充说明最多2000字符");
        if ("WAITING_INPUT".equals(previous.getStatus()) && (answer == null || answer.isBlank()))
            throw new ResponseStatusException(BAD_REQUEST, "请先回答澄清问题");
        ChatRequest request = new ChatRequest();
        request.setConversationId(previous.getConversationId());
        request.setModel(previous.getModel());
        request.setRequestId("resume_" + id);
        request.setWebSearchEnabled(previous.isWebSearchEnabled());
        request.setMessage(answer == null || answer.isBlank() ? "继续完成原始目标" : answer);
        return start(request, previous);
    }

    private SseEmitter start(ChatRequest request, AgentRun parent) {
        Long userId = UserContext.getCurrentUserId();
        if (request.getMessage() == null || request.getMessage().isBlank() || request.getMessage().length() > 8000)
            throw new ResponseStatusException(BAD_REQUEST, "消息应为1到8000字符");
        String model = models.requireModelName(request.getModel());
        var prior = store.findRequest(request.getRequestId(), userId);
        if (prior.isPresent()) return replay(prior.get(), request);
        if (!capacity.tryAcquire()) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Agent繁忙，请稍后重试");
        AgentRun run = new AgentRun();
        boolean created = false;
        Long newConversationId = null;
        if (request.getRequestId() != null) run.setRequestId(request.getRequestId());
        try {
            AiConversation conversation;
            if (request.getConversationId() == null) {
                conversation = new AiConversation();
                conversation.setUserId(userId);
                conversation.setModel(model);
                conversation.setMode("AGENT");
                conversation.setTitle(request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
                conversation.setCount(0L);
                conversation.setTotalTokens(0L);
                conversations.insert(conversation);
                newConversationId = conversation.getId();
            } else {
                conversation = conversations.selectById(request.getConversationId());
                if (conversation == null || !userId.equals(conversation.getUserId()))
                    throw new ResponseStatusException(NOT_FOUND, "会话不存在");
                if (!"AGENT".equals(conversation.getMode()))
                    throw new ResponseStatusException(CONFLICT, "请新建Agent会话，普通聊天与Agent使用独立会话");
            }
            run.setUserId(userId);
            run.setConversationId(conversation.getId());
            run.setModel(model);
            run.setMessage(request.getMessage());
            run.setParentRunId(parent == null ? null : parent.getId());
            run.setContinuationDepth(parent == null ? 0 : parent.getContinuationDepth() + 1);
            run.setPriorTokens(parent == null ? 0 : parent.getPriorTokens() + Math.max(parent.totalTokens(), parent.getBudgetTokens()));
            run.setGoal(parent == null ? request.getMessage() : parent.getGoal() == null ? parent.getMessage() : parent.getGoal());
            if (parent != null) {
                var state = run.getTaskState();
                state.getInstructions().addAll(parent.getTaskState().getInstructions());
                if (state.getInstructions().isEmpty()) state.getInstructions().add(run.getGoal());
                state.getObservations().putAll(parent.getTaskState().getObservations());
                state.getDocuments().putAll(parent.getTaskState().getDocuments());
                state.getDrafts().putAll(parent.getTaskState().getDrafts());
                run.getSourceFileIds().addAll(parent.getSourceFileIds());
            }
            run.getTaskState().getInstructions().add(request.getMessage());
            run.setWebSearchEnabled(request.isWebSearchEnabled());
            run.setDeadline(LocalDateTime.now().plusSeconds(config.getTimeoutSeconds()));
            store.expire();
            store.create(run);
            created = true;
            SseEmitter emitter = new SseEmitter((config.getTimeoutSeconds() + 5) * 1000L);
            Active job = new Active(run, emitter);
            active.put(run.getId(), job);
            emitter.onCompletion(() -> {
                if (!job.finished.get()) job.stop();
            });
            emitter.onTimeout(job::stop);
            emitter.onError(e -> job.stop());
            job.send("agent_started", Map.of("runId", run.getId(), "conversationId", run.getConversationId(), "model", model));
            workers.execute(() -> process(job, request.isWebSearchEnabled()));
            return emitter;
        } catch (RuntimeException e) {
            capacity.release();
            active.remove(run.getId());
            if (created) {
                run.setStatus("FAILED");
                run.setError("TASK_START_FAILED");
                store.finish(run);
            }
            if (!created && newConversationId != null) conversations.deleteById(newConversationId);
            if (e instanceof DuplicateKeyException && request.getRequestId() != null) {
                var existing = store.findRequest(request.getRequestId(), userId);
                if (existing.isPresent()) return replay(existing.get(), request);
            }
            if (e instanceof DuplicateKeyException)
                throw new ResponseStatusException(CONFLICT, "当前用户已有Agent任务在执行");
            throw e;
        }
    }

    private SseEmitter replay(AgentRun run, ChatRequest request) {
        if (!run.getMessage().equals(request.getMessage()) || !run.getModel().equals(models.requireModelName(request.getModel())) ||
                (request.getConversationId() != null && !request.getConversationId().equals(run.getConversationId())))
            throw new ResponseStatusException(CONFLICT, "requestId已用于不同请求");
        SseEmitter emitter = new SseEmitter(10000L);
        try {
            emitter.send(SseEmitter.event().name("agent_started").data(Map.of("runId", run.getId(), "conversationId", run.getConversationId(), "replayed", true)));
            emitter.send(SseEmitter.event().name("agent_snapshot").data(run));
            emitter.send(SseEmitter.event().name("agent_actions").data(actions.list(run.getId(), run.getUserId())));
            emitter.send(SseEmitter.event().name("done").data(Map.of("runId", run.getId(), "conversationId", run.getConversationId(), "status", run.getStatus())));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void process(Active job, boolean web) {
        AgentRun run = job.run;
        job.thread = Thread.currentThread();
        try {
            List<ChatMessage> history = AgentHistory.build(run, store.history(run.getConversationId(), run.getUserId()), actions);
            engine.execute(run, models.getChatModel(run.getModel()), tools.create(run, web), history, job::send);
            run.setStatus(run.getPauseReason() != null ? run.getPauseReason() : actions.list(run.getId(), run.getUserId()).isEmpty() ? "SUCCEEDED" : "WAITING_CONFIRMATION");
        } catch (CancellationException e) {
            run.setStatus(LocalDateTime.now().isBefore(run.getDeadline()) ? "CANCELLED" : "TIMED_OUT");
            run.setError(run.getStatus());
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setError(e instanceof AgentExecutionException ? e.getMessage() : "AGENT_EXECUTION_FAILED");
            log.error("Agent run failed: runId={}, userId={}, model={}", run.getId(), run.getUserId(), run.getModel(), e);
        } finally {
            Thread.interrupted();
            if (run.getPlan() != null) for (var step : run.getPlan().getSteps())
                if ("RUNNING".equals(step.getStatus())) step.setStatus("FAILED");
            try {
                store.finish(run);
                run.setStatus(store.get(run.getId(), run.getUserId()).getStatus());
                job.send("agent_snapshot", run);
                if (Set.of("SUCCEEDED", "WAITING_CONFIRMATION", "WAITING_INPUT").contains(run.getStatus()))
                    job.send("agent_actions", actions.list(run.getId(), run.getUserId()));
                else
                    job.send("error", Map.of("runId", run.getId(), "error", run.getError() == null ? run.getStatus() : run.getError()));
                job.send("done", Map.of("runId", run.getId(), "conversationId", run.getConversationId(), "status", run.getStatus(), "model", run.getModel(),
                        "usage", Map.of("inputTokens", run.getInputTokens(), "outputTokens", run.getOutputTokens(), "totalTokens", run.totalTokens(), "complete", run.isUsageComplete())));
            } catch (CancellationException ignored) {
                // The persisted result remains queryable after the HTTP client disconnects.
            } catch (Exception e) {
                log.error("Agent finalization failed: runId={}", run.getId(), e);
            }
            job.finished.set(true);
            job.emitter.complete();
            active.remove(run.getId());
            capacity.release();
            metrics.counter("ai.agent.runs", "status", run.getStatus()).increment();
            log.info("Agent run ended: runId={}, status={}, modelCalls={}, toolCalls={}, tokens={}", run.getId(), run.getStatus(), run.getModelCalls(), run.getToolCalls(), run.totalTokens());
        }
    }

    private void heartbeat() {
        try {
            store.expire();
        } catch (Exception e) {
            log.error("Agent state maintenance failed", e);
        }
        active.values().forEach(job -> {
            try {
                if (store.shouldStop(job.run.getId())) {
                    if (job.thread != null) job.thread.interrupt();
                } else job.send("heartbeat", Map.of("runId", job.run.getId()));
            } catch (Exception e) {
                job.stop();
            }
        });
    }

    @PreDestroy
    public void close() {
        active.values().forEach(Active::stop);
        workers.shutdownNow();
        scheduler.shutdownNow();
    }

    private class Active {
        final AgentRun run;
        final SseEmitter emitter;
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicBoolean disconnected = new AtomicBoolean();
        volatile Thread thread;

        Active(AgentRun run, SseEmitter emitter) {
            this.run = run;
            this.emitter = emitter;
        }

        synchronized void send(String event, Object data) {
            if (disconnected.get()) throw new CancellationException();
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                stop();
                throw new CancellationException();
            }
        }

        void stop() {
            if (finished.get() || !disconnected.compareAndSet(false, true)) return;
            try {
                store.cancel(run.getId(), run.getUserId());
            } catch (Exception e) {
                log.warn("Agent cancellation persistence failed: runId={}", run.getId(), e);
            }
            Thread current = thread;
            if (current != null) current.interrupt();
        }
    }
}
