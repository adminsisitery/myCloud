package com.ff.mycloud.ai.agent;

import com.ff.mycloud.ai.agent.action.AgentAction;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.common.util.UserContext;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentTaskController {
    private final AgentRunStore runs;
    private final AgentActionService actions;
    private final AgentOrchestrator orchestrator;

    public AgentTaskController(AgentRunStore runs, AgentActionService actions, AgentOrchestrator orchestrator) {
        this.runs = runs;
        this.actions = actions;
        this.orchestrator = orchestrator;
    }

    public record ResumeRequest(@jakarta.validation.constraints.Size(max = 2000) String answer) {
    }

    @PostMapping(value = "/runs/{id}/resume", produces = "text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter resume(@PathVariable String id, @jakarta.validation.Valid @RequestBody ResumeRequest body) {
        return orchestrator.resume(id, body.answer());
    }

    @GetMapping("/conversations/{conversationId}/audit")
    public java.util.List<AgentAction> audit(@PathVariable Long conversationId, @RequestParam(defaultValue = "0") int offset) {
        return actions.audit(UserContext.getCurrentUserId(), conversationId, offset);
    }

    @PostMapping("/actions/{id}/reconcile")
    public AgentAction reconcile(@PathVariable String id) {
        return actions.reconcile(id, UserContext.getCurrentUserId());
    }

    @GetMapping("/actions/{id}/document")
    public org.springframework.http.ResponseEntity<byte[]> document(@PathVariable String id) {
        var a = actions.get(id, UserContext.getCurrentUserId());
        if (a.getContent() == null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment().filename(a.getName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).body(a.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/runs/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        Long userId = UserContext.getCurrentUserId();
        return Map.of("run", runs.get(id, userId), "actions", actions.list(id, userId));
    }

    @GetMapping("/conversations/{conversationId}/runs")
    public java.util.List<AgentRun> history(@PathVariable Long conversationId) {
        return runs.history(conversationId, UserContext.getCurrentUserId());
    }

    @PostMapping("/runs/{id}/cancel")
    public Map<String, String> cancel(@PathVariable String id) {
        runs.cancel(id, UserContext.getCurrentUserId());
        return Map.of("status", "cancel_requested");
    }

    @PostMapping("/actions/{id}/confirm")
    public AgentAction confirm(@PathVariable String id) {
        return actions.confirm(id, UserContext.getCurrentUserId());
    }

    @PostMapping("/actions/{id}/reject")
    public AgentAction reject(@PathVariable String id) {
        return actions.reject(id, UserContext.getCurrentUserId());
    }
}
