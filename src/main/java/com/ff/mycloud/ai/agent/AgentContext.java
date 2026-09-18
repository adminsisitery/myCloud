package com.ff.mycloud.ai.agent;

import dev.langchain4j.data.message.*;
import java.util.*;

/** Evict complete tool exchanges only. Full observations remain in the durable task state. */
final class AgentContext {
    private final List<ChatMessage> pinned;
    private final List<List<ChatMessage>> exchanges = new ArrayList<>();
    private final AgentRun run;
    private boolean compacted;

    AgentContext(AgentRun run, List<ChatMessage> pinned) {
        this.run = run;
        this.pinned = List.copyOf(pinned);
        this.compacted = !run.getTaskState().getObservations().isEmpty();
    }

    void addExchange(List<ChatMessage> exchange) { exchanges.add(List.copyOf(exchange)); }

    List<ChatMessage> messages() {
        var result = new ArrayList<>(pinned);
        if (compacted) {
            StringBuilder index = new StringBuilder("较早工具结果已归档，不能根据缺失正文推测结论。用 readStoredResult 按 observationId 分页回查；更早的索引用 listStoredResults 查询。\n");
            var observations = new ArrayList<>(run.getTaskState().getObservations().values());
            for (var observation : observations.subList(Math.max(0, observations.size() - 20), observations.size())) {
                index.append(observation.id()).append(' ').append(observation.tool())
                        .append(observation.success() ? " SUCCESS" : " FAILED")
                        .append(" 参数摘要（可能截断）：")
                        .append(observation.arguments(), 0, Math.min(180, observation.arguments().length())).append('\n');
            }
            index.append("阅读进度：").append(run.getTaskState().getDocuments());
            result.add(UserMessage.from(index.toString()));
            result.add(AiMessage.from("需要原文依据时回查已保存记录。"));
        }
        exchanges.forEach(result::addAll);
        return result;
    }

    boolean compactOldest() {
        if (exchanges.size() <= 1) return false;
        exchanges.removeFirst();
        compacted = true;
        return true;
    }
}
