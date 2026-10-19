package com.cloudrive.aiService.observability;

import org.springframework.boot.actuate.health.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("agentSchema")
public class AgentHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;

    public AgentHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            jdbc.queryForList("SELECT id,request_id,finalized FROM t_ai_agent_run LIMIT 0");
            jdbc.queryForList("SELECT id,status FROM t_ai_agent_action LIMIT 0");
            jdbc.queryForList("SELECT id,mode,memory_state,total_tokens FROM t_ai_conversation LIMIT 0");
            jdbc.queryForList("SELECT conversation_id,version,summary,through_message_id,history_gap,lease_id,lease_until FROM t_ai_chat_memory LIMIT 0");
            jdbc.queryForList("SELECT id,request_id,user_message_id,assistant_message_id,status,messages FROM t_ai_chat_turn LIMIT 0");
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", "AI database migrations 001/003 required or database unavailable").build();
        }
    }
}
