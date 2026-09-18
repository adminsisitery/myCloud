package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

/** MySQL is the source of truth; unique active slots also work across replicas. */
@Repository
public class AgentRunStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AgentRunStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public void create(AgentRun run) {
        if (jdbc.queryForList("SELECT id FROM t_ai_conversation WHERE id=? AND user_id=? AND mode='AGENT' FOR UPDATE",run.getConversationId(),run.getUserId()).isEmpty())
            throw new ResponseStatusException(CONFLICT,"Agent会话已删除或不可用");
        jdbc.update("INSERT INTO t_ai_agent_run(id,request_id,user_id,conversation_id,model,status,snapshot,deadline,active_user,active_conversation) VALUES(?,?,?,?,?,?,?,?,?,?)",
                run.getId(), run.getRequestId(), run.getUserId(), run.getConversationId(), run.getModel(), run.getStatus(), encode(run), run.getDeadline(), run.getUserId(), run.getConversationId());
    }

    public java.util.Optional<AgentRun> findRequest(String requestId, Long userId) {
        if (requestId == null) return java.util.Optional.empty();
        return jdbc.query("SELECT snapshot,status FROM t_ai_agent_run WHERE request_id=? AND user_id=?", (rs,n)->{
            var run=decode(rs.getString(1));run.setStatus(rs.getString(2));return run;
        },requestId,userId).stream().findFirst();
    }

    public boolean save(AgentRun run) {
        return jdbc.update("UPDATE t_ai_agent_run SET status=?,snapshot=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('QUEUED','RUNNING') AND cancel_requested=0 AND deadline>CURRENT_TIMESTAMP",
                run.getStatus(), encode(run), run.getId()) == 1;
    }

    public boolean shouldStop(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT cancel_requested=1 OR deadline<=CURRENT_TIMESTAMP OR status NOT IN ('QUEUED','RUNNING') FROM t_ai_agent_run WHERE id=?", Boolean.class, id));
    }

    public AgentRun get(String id, Long userId) {
        var runs = jdbc.query("SELECT snapshot,status FROM t_ai_agent_run WHERE id=? AND user_id=?", (rs, n) -> {
            AgentRun run = decode(rs.getString(1)); run.setStatus(rs.getString(2)); return run;
        }, id, userId);
        if (runs.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "任务不存在");
        return runs.getFirst();
    }

    public List<AgentRun> history(Long conversationId, Long userId) {
        return history(conversationId, userId, 0);
    }

    public List<AgentRun> history(Long conversationId, Long userId, int offset) {
        if (offset < 0 || offset > 10000) throw new IllegalArgumentException("历史偏移无效");
        return jdbc.query("SELECT snapshot,status FROM t_ai_agent_run WHERE conversation_id=? AND user_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
                (rs, n) -> { var r = decode(rs.getString(1)); r.setStatus(rs.getString(2)); return r; }, conversationId, userId, offset)
                .stream().sorted(java.util.Comparator.comparing(AgentRun::getCreatedAt).reversed()).toList();
    }

    public void cancel(String id, Long userId) {
        get(id, userId);
        jdbc.update("UPDATE t_ai_agent_run SET cancel_requested=1 WHERE id=? AND user_id=? AND status IN ('QUEUED','RUNNING')", id, userId);
        jdbc.update("UPDATE t_ai_agent_action SET status='REJECTED' WHERE run_id=? AND user_id=? AND status='PENDING'", id, userId);
    }

    @Transactional
    public void finish(AgentRun run) {
        // Cancellation wins a race with successful completion.
        String persisted = jdbc.queryForObject("SELECT status FROM t_ai_agent_run WHERE id=? FOR UPDATE",String.class,run.getId());
        if ("TIMED_OUT".equals(persisted)) run.setStatus("TIMED_OUT");
        else if (java.util.Set.of("SUCCEEDED", "WAITING_CONFIRMATION", "WAITING_INPUT").contains(run.getStatus()) && shouldStop(run.getId())) run.setStatus("CANCELLED");
        int changed = jdbc.update("UPDATE t_ai_agent_run SET status=?,snapshot=?,finalized=1,active_user=NULL,active_conversation=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND finalized=0",
                run.getStatus(), encode(run), run.getId());
        if (changed == 0) return;
        if (!java.util.Set.of("SUCCEEDED", "WAITING_CONFIRMATION", "WAITING_INPUT").contains(run.getStatus())) jdbc.update("UPDATE t_ai_agent_action SET status='REJECTED' WHERE run_id=? AND status='PENDING'", run.getId());
        jdbc.update("INSERT INTO t_ai_message(conversation_id,role,content,create_time) VALUES(?,'USER',?,CURRENT_TIMESTAMP)", run.getConversationId(), run.getMessage());
        String answer = run.getOutput().isBlank() ? "任务状态：" + run.getStatus() : run.getOutput();
        jdbc.update("INSERT INTO t_ai_message(conversation_id,role,content,input_tokens,output_tokens,create_time) VALUES(?,'ASSISTANT',?,?,?,CURRENT_TIMESTAMP)", run.getConversationId(), answer, run.getInputTokens(), run.getOutputTokens());
        jdbc.update("UPDATE t_ai_conversation SET count=COALESCE(count,0)+1,total_tokens=COALESCE(total_tokens,0)+?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", run.totalTokens(), run.getConversationId(), run.getUserId());
        jdbc.update("INSERT INTO t_ai_usage_log(user_id,conversation_id,model,scene,prompt_tokens,completion_tokens,total_tokens,tool_call_count,latency_ms,created_at) VALUES(?,?,?,'agent',?,?,?,?,?,CURRENT_TIMESTAMP)",
                run.getUserId(), run.getConversationId(), run.getModel(), run.getInputTokens(), run.getOutputTokens(), run.totalTokens(), run.getToolCalls(), java.time.Duration.between(run.getCreatedAt(), java.time.LocalDateTime.now()).toMillis());
    }

    @Transactional
    public void expire() {
        // No replay after a crash: this is especially important for user-confirmed actions.
        jdbc.update("UPDATE t_ai_agent_action SET status='EXPIRED' WHERE status='PENDING' AND expires_at<=CURRENT_TIMESTAMP");
        jdbc.update("UPDATE t_ai_agent_action SET status='UNKNOWN' WHERE status='EXECUTING' AND updated_at<?", java.time.LocalDateTime.now().minusMinutes(15));
        jdbc.update("UPDATE t_ai_agent_action SET status='REJECTED' WHERE status='PENDING' AND run_id IN (SELECT id FROM t_ai_agent_run WHERE deadline<=CURRENT_TIMESTAMP AND status IN ('QUEUED','RUNNING'))");
        jdbc.update("UPDATE t_ai_agent_run SET status='TIMED_OUT',active_user=NULL,active_conversation=NULL,updated_at=CURRENT_TIMESTAMP WHERE status IN ('QUEUED','RUNNING') AND deadline<=CURRENT_TIMESTAMP");
    }

    private String encode(AgentRun run) {
        try {
            var snapshot = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(run);
            // Task evidence is stored in MySQL but excluded from public SSE/API snapshots.
            snapshot.set("taskState", json.valueToTree(run.getTaskState()));
            return json.writeValueAsString(snapshot);
        } catch (Exception e) { throw new IllegalStateException("Cannot persist agent run", e); }
    }

    private AgentRun decode(String value) {
        try {
            var snapshot = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(value);
            var state = snapshot.remove("taskState");
            AgentRun run = json.treeToValue(snapshot, AgentRun.class);
            if (state != null && !state.isNull()) run.setTaskState(json.treeToValue(state, AgentTaskState.class));
            return run;
        } catch (Exception e) { throw new IllegalStateException("Invalid agent run snapshot", e); }
    }
}
