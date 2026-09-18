package com.ff.mycloud.ai.agent;

import com.ff.mycloud.ai.agent.action.AgentActionService;
import dev.langchain4j.data.message.*;
import java.util.*;

/** Keep recent complete turns, including failures. Never truncate away a user's final constraint. */
final class AgentHistory {
    static List<ChatMessage> build(AgentRun current, List<AgentRun> previous, AgentActionService actions) {
        List<List<ChatMessage>> groups = new ArrayList<>();
        int remaining = 12000;
        for (AgentRun old : previous) { // Store returns newest first.
            if (old.getId().equals(current.getId())) continue;
            String input = old.getMessage();
            String outcome = "历史任务状态：" + old.getStatus() + "\n" + old.getOutput();
            String decisions = "历史操作实际结果（不得重复成功或被拒绝的操作）：" + actions.list(old.getId(), current.getUserId()).stream()
                    .map(a -> a.getDescription() + "=" + a.getStatus() + ", resultFileId=" + a.getResultFileId()).toList();
            int size = input.length() + outcome.length() + decisions.length();
            if (size > remaining) {
                current.getTaskState().setHistoryIncomplete(true);
                continue;
            }
            remaining -= size;
            groups.add(List.of(UserMessage.from(input), AiMessage.from(outcome), UserMessage.from(decisions)));
        }
        Collections.reverse(groups);
        List<ChatMessage> result = new ArrayList<>();
        groups.forEach(result::addAll);
        if (current.getTaskState().isHistoryIncomplete()) result.add(UserMessage.from("部分旧任务未装入上下文；用recallTaskInstructions回查数据库，不能猜测缺失要求。当前续跑链的完整用户要求另行保留。"));
        return result;
    }
}
