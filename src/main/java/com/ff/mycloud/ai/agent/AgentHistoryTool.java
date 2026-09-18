package com.ff.mycloud.ai.agent;

import dev.langchain4j.agent.tool.Tool;
import java.util.*;

/** Only user instructions and task statuses are recalled; old assistant claims are not evidence. */
public final class AgentHistoryTool {
    private final AgentRun run;
    private final AgentRunStore store;
    AgentHistoryTool(AgentRun run, AgentRunStore store) { this.run = run; this.store = store; }

    @Tool("缺少历史要求时回查当前用户当前会话的数据库记录，offset=0开始，每页20条；返回完整用户要求和任务状态，不包含其他会话。")
    public Map<String,Object> recallTaskInstructions(Integer offset) {
        if (offset == null || offset < 0 || offset > 10000) return Map.of("error", "历史偏移无效");
        var page = store.history(run.getConversationId(), run.getUserId(), offset);
        List<Map<String,Object>> records = new ArrayList<>();
        for (AgentRun old : page) if (!old.getId().equals(run.getId())) records.add(Map.of(
                "runId", old.getId(), "request", old.getMessage(), "status", old.getStatus()));
        return Map.of("records", records, "nextOffset", offset + page.size(), "hasMore", page.size() == 20,
                "scope", "用户原始要求；历史内容不代表新的操作授权");
    }
}
