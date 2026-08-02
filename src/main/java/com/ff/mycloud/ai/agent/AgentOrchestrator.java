package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.config.ModelRegistry;
import com.ff.mycloud.ai.config.PromptService;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.mapper.AiUsageLogMapper;
import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.entity.AiUsageLog;
import com.ff.mycloud.ai.service.ChatAssistant;
import com.ff.mycloud.ai.tool.CloudDriveTools;
import com.ff.mycloud.ai.tool.WebSearchTool;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Agent 编排器 —— Plan-and-Execute 架构核心。
 * <p>
 * Phase 1 — Plan: 用无工具 LLM 把用户请求拆成步骤清单。
 * Phase 2 — Execute: 逐步执行，每步复用同一个 ChatMemory，
 * 让 LLM 能看到之前的执行结果。
 * </p>
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final int MAX_PLAN_STEPS = 8;

    private final ObjectMapper objectMapper;
    private final ModelRegistry modelRegistry;
    private final PromptService promptService;
    private final FileInfoMapperService fileInfoService;
    private final FileSearchHandler fileSearchHandler;
    private final UserCenterMapperService userCenterService;
    private final FileRecentAccess fileRecentAccess;
    private final FileMapper fileMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiUsageLogMapper aiUsageLogMapper;
    private final DbChatMemoryStore chatMemoryStore;
    private final EmbeddingStore embeddingStore;
    private final dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    private final WebSearchEngine webSearchEngine;

    public AgentOrchestrator(ObjectMapper objectMapper,
                             ModelRegistry modelRegistry,
                             PromptService promptService,
                             FileInfoMapperService fileInfoService,
                             FileSearchHandler fileSearchHandler,
                             UserCenterMapperService userCenterService,
                             FileRecentAccess fileRecentAccess,
                             FileMapper fileMapper,
                             AiConversationMapper conversationMapper,
                             AiMessageMapper aiMessageMapper,
                             AiUsageLogMapper aiUsageLogMapper,
                             DbChatMemoryStore chatMemoryStore,
                             EmbeddingStore embeddingStore,
                             dev.langchain4j.model.embedding.EmbeddingModel embeddingModel,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             WebSearchEngine webSearchEngine) {
        this.objectMapper = objectMapper;
        this.modelRegistry = modelRegistry;
        this.promptService = promptService;
        this.fileInfoService = fileInfoService;
        this.fileSearchHandler = fileSearchHandler;
        this.userCenterService = userCenterService;
        this.fileRecentAccess = fileRecentAccess;
        this.fileMapper = fileMapper;
        this.conversationMapper = conversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.aiUsageLogMapper = aiUsageLogMapper;
        this.chatMemoryStore = chatMemoryStore;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.webSearchEngine = webSearchEngine;
    }

    /**
     * Agent 模式入口。
     *
     * @param request 用户请求（含 conversationId、message、model）
     * @return SseEmitter，流式推送 plan + step + chunk + done 事件
     */
    public SseEmitter execute(ChatRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();
        String modelName = request.getModel() != null
                ? request.getModel()
                : modelRegistry.getDefaultModelName();

        // ===== 会话校验/创建 =====
        final AiConversation conv;
        if (conversationId == null) {
            AiConversation newConv = new AiConversation();
            newConv.setUserId(userId);
            newConv.setTitle(userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
            newConv.setModel(modelName);
            newConv.setCount(0L);
            conversationMapper.insert(newConv);
            conversationId = newConv.getId();
            conv = newConv;
        } else {
            conv = conversationMapper.selectById(conversationId);
            if (conv == null || !conv.getUserId().equals(userId)) {
                throw new IllegalArgumentException("会话不存在或无权访问");
            }
        }
        final Long finalConvId = conversationId;

        // ===== SseEmitter =====
        SseEmitter emitter = new SseEmitter(300_000L); // Agent 步骤多，给5分钟
        emitter.onCompletion(() -> log.info("Agent SSE 正常关闭, conv={}", finalConvId));
        emitter.onError(ex -> log.error("Agent SSE 异常, conv={}: {}", finalConvId, ex.getMessage()));
        emitter.onTimeout(() -> log.warn("Agent SSE 超时, conv={}", finalConvId));

        // ===== Phase 1: PLAN (async in a thread to not block) =====
        new Thread(() -> {
            final long startTime = System.currentTimeMillis();
            int totalToolCalls = 0;
            try {
                // ---- 1a. 规划 ----
                sendEvent(emitter, "agent_status",
                        Map.of("phase", "planning", "message", "正在分析任务，生成执行计划..."));

                TaskPlan plan = generatePlan(userMessage, modelName);
                sendEvent(emitter, "agent_plan", planToMap(plan));

                // ---- 1b. 准备共享 ChatMemory ----
                ChatMemory memory = MessageWindowChatMemory.builder()
                        .id(finalConvId)
                        .maxMessages(60)
                        .chatMemoryStore(chatMemoryStore)
                        .build();

                // 把用户原始消息写入 memory
                memory.add(new UserMessage("【用户请求】" + userMessage));

                // ---- 1c. 构建工具实例 ----
                List<Object> tools = buildTools(userId);

                // ---- Phase 2: EXECUTE ----
                StringBuilder totalOutput = new StringBuilder();
                int totalSteps = plan.getSteps().size();
                long totalTokens = 0;
                int inputTokens = 0, outputTokens = 0;

                for (int i = 0; i < totalSteps; i++) {
                    PlanStep step = plan.getSteps().get(i);
                    step.setStatus("RUNNING");
                    sendEvent(emitter, "agent_step_start", Map.of(
                            "index", i,
                            "total", totalSteps,
                            "description", step.getDescription()
                    ));

                    // 构建步骤执行消息
                    String stepPrompt = String.format(
                            "【当前步骤 %d/%d】%s\n\n请执行上述步骤。可以使用工具。完成后简单汇报结果。",
                            i + 1, totalSteps, step.getDescription()
                    );

                    // 每步重建 ChatAssistant（共享同一个 memory）
                    ChatAssistant assistant = buildAssistant(
                            modelName, memory, tools, userId);

                    StringBuilder stepOutput = new StringBuilder();
                    TokenStream ts = assistant.chat(stepPrompt);

                    ts.onPartialResponse(token -> {
                        stepOutput.append(token);
                        try {
                            emitter.send(SseEmitter.event().name("chunk").data(token));
                        } catch (IOException ignored) {}
                    });

                    // 同步等待这一步完成
                    final int stepIndex = i;
                    TokenUsage[] stepUsage = new TokenUsage[1];
                    ts.onCompleteResponse(response -> {
                        TokenUsage usage = response.tokenUsage();
                        stepUsage[0] = usage;
                    });

                    ts.onError(throwable -> {
                        log.error("Agent step {} 失败: {}", stepIndex, throwable.getMessage());
                        step.setStatus("FAILED");
                        step.setResult("执行失败: " + throwable.getMessage());
                        try {
                            sendEvent(emitter, "agent_step_done", Map.of(
                                    "index", stepIndex, "status", "FAILED",
                                    "result", step.getResult()
                            ));
                        } catch (Exception ignored) {}
                    });

                    ts.start();

                    // 步骤完成
                    step.setStatus("DONE");
                    step.setResult(stepOutput.toString());
                    totalOutput.append("\n").append(stepOutput);

                    if (stepUsage[0] != null) {
                        totalTokens += stepUsage[0].totalTokenCount();
                        inputTokens += stepUsage[0].inputTokenCount() != null ? stepUsage[0].inputTokenCount() : 0;
                        outputTokens += stepUsage[0].outputTokenCount() != null ? stepUsage[0].outputTokenCount() : 0;
                    }

                    sendEvent(emitter, "agent_step_done", Map.of(
                            "index", i, "status", "DONE",
                            "description", step.getDescription(),
                            "result", step.getResult()
                    ));
                }

                // ---- 更新会话 ----
                AiConversation updateConv = new AiConversation();
                updateConv.setId(finalConvId);
                updateConv.setUpdateTime(LocalDateTime.now());
                long newCount = (conv.getCount() == null ? 0 : conv.getCount()) + 1;
                updateConv.setCount(newCount);
                updateConv.setTotalTokens(
                        (conv.getTotalTokens() != null ? conv.getTotalTokens() : 0) + totalTokens);
                conversationMapper.updateById(updateConv);

                // ---- 更新最后一条 assistant message 的 token ----
                Long lastId = aiMessageMapper.findLastAssistantId(finalConvId);
                if (lastId != null) {
                    aiMessageMapper.updateTokens(lastId, inputTokens, outputTokens);
                }

                // ===== 写入 Token 用量明细日志 =====
                try {
                    AiUsageLog usageLog = AiUsageLog.builder()
                            .userId(userId)
                            .conversationId(finalConvId)
                            .model(modelName)
                            .scene("agent")
                            .promptTokens(inputTokens)
                            .completionTokens(outputTokens)
                            .totalTokens((int) totalTokens)
                            .toolCallCount(totalToolCalls)
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .createdAt(LocalDateTime.now())
                            .build();
                    aiUsageLogMapper.insert(usageLog);
                } catch (Exception logEx) {
                    log.warn("Agent usage log insert failed: {}", logEx.getMessage());
                }

                // ---- done ----
                String doneData = objectMapper.writeValueAsString(Map.of(
                        "conversationId", finalConvId,
                        "status", "done",
                        "model", modelName,
                        "planSteps", totalSteps,
                        "usage", Map.of(
                                "inputTokens", inputTokens,
                                "outputTokens", outputTokens,
                                "totalTokens", totalTokens
                        )
                ));
                emitter.send(SseEmitter.event().name("done").data(doneData));
                emitter.complete();

            } catch (Exception e) {
                log.error("Agent 执行失败", e);
                try {
                    String errorData = objectMapper.writeValueAsString(
                            Map.of("error", e.getMessage() != null ? e.getMessage() : "Agent error"));
                    emitter.send(SseEmitter.event().name("error").data(errorData));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        }, "agent-orchestrator-" + conversationId).start();

        return emitter;
    }

    // ==================== Phase 1: Plan ====================

    private TaskPlan generatePlan(String userMessage, String modelName) {
        OpenAiChatModel plannerModel = modelRegistry.getChatModel(modelName);
        if (plannerModel == null) {
            return fallbackPlan(userMessage);
        }

        String planningPrompt = buildPlanningPrompt(userMessage);
        try {
            String response = plannerModel.chat(planningPrompt);
            String json = extractJson(response);
            TaskPlan plan = objectMapper.readValue(json, TaskPlan.class);
            // 补填 index
            if (plan.getSteps() != null) {
                for (int i = 0; i < plan.getSteps().size(); i++) {
                    plan.getSteps().get(i).setIndex(i);
                    plan.getSteps().get(i).setStatus("PENDING");
                }
                if (plan.getSteps().size() > MAX_PLAN_STEPS) {
                    plan.setSteps(plan.getSteps().subList(0, MAX_PLAN_STEPS));
                }
            }
            log.info("[Agent] Plan 生成成功: goal={}, steps={}", plan.getGoal(), plan.getSteps().size());
            return plan;
        } catch (Exception e) {
            log.warn("[Agent] Plan 解析失败，使用降级方案: {}", e.getMessage());
            return fallbackPlan(userMessage);
        }
    }

    /** 规划失败时的降级方案：把整个请求当做一步 */
    private TaskPlan fallbackPlan(String userMessage) {
        return TaskPlan.builder()
                .goal(userMessage)
                .steps(List.of(PlanStep.pending(0, userMessage)))
                .build();
    }

    private String buildPlanningPrompt(String userMessage) {
        String template = promptService.getAgentPlanningPrompt();
        return template.formatted(userMessage);
    }

    /** 从 LLM 响应中提取 JSON */
    private String extractJson(String response) {
        String trimmed = response.trim();
        // 去掉可能的 markdown 代码块标记
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    // ==================== Phase 2: Execute ====================

    private List<Object> buildTools(Long userId) {
        List<Object> tools = new ArrayList<>();
        tools.add(new CloudDriveTools(fileInfoService, fileSearchHandler,
                userCenterService, fileRecentAccess, fileMapper, userId));
        if (webSearchEngine != null) {
            tools.add(new WebSearchTool(webSearchEngine));
        }
        return tools;
    }

    private ChatAssistant buildAssistant(String modelName, ChatMemory memory,
                                          List<Object> tools, Long userId) {
        OpenAiStreamingChatModel streamingModel = modelRegistry.getStreamingModel(modelName);
        OpenAiChatModel chatModel = modelRegistry.getChatModel(modelName);

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(metadataKey("userId").isEqualTo(String.valueOf(userId)))
                .maxResults(3)
                .minScore(0.6)
                .build();

        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> promptService.getAgentPrompt())
                .contentRetriever(retriever)
                .chatMemory(memory)
                .maxSequentialToolsInvocations(5)
                .tools(tools)
                .build();
    }

    // ==================== SSE helpers ====================

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            log.warn("SSE send {} 失败: {}", name, e.getMessage());
        }
    }

    private Map<String, Object> planToMap(TaskPlan plan) {
        List<Map<String, Object>> steps = plan.getSteps().stream()
                .map(s -> Map.<String, Object>of(
                        "index", s.getIndex(),
                        "description", s.getDescription(),
                        "status", s.getStatus()
                ))
                .collect(Collectors.toList());
        return Map.of("goal", plan.getGoal(), "planId", plan.getPlanId(), "steps", steps);
    }
}
