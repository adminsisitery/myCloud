package com.ff.mycloud.ai.agent;

import com.ff.mycloud.ai.agent.plan.TaskPlan;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class AgentRun {
    private String id = UUID.randomUUID().toString();
    private String requestId = UUID.randomUUID().toString();
    private Long userId;
    private Long conversationId;
    private String model;
    private String message;
    private String status = "QUEUED";
    private TaskPlan plan;
    private String output = "";
    private String error;
    private String parentRunId;
    private String goal;
    private String clarification;
    private String pauseReason;
    private boolean webSearchEnabled;
    private int continuationDepth;
    private int priorTokens;
    /** Conservative model-call budget accounting; separate from provider-reported billing. */
    private int budgetTokens;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AgentTaskState taskState = new AgentTaskState();
    private java.util.Set<Long> sourceFileIds = new java.util.LinkedHashSet<>();
    private int inputTokens;
    private int outputTokens;
    private int toolCalls;
    private int modelCalls;
    private boolean usageComplete = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime deadline;

    public int totalTokens() { return inputTokens + outputTokens; }
}
