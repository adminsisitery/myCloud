package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** Optional integration against a freshly migrated disposable MySQL database. Never use production. */
@EnabledIfSystemProperty(named="agent.mysql.url",matches=".+")
class AgentMySqlTest {
    @Test void productionSqlSupportsPersistenceExpiryAndRecovery() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(System.getProperty("agent.mysql.url"),System.getProperty("agent.mysql.user","root"),System.getProperty("agent.mysql.password","")));
        long conversation=System.currentTimeMillis();
        long user=conversation;
        jdbc.update("INSERT INTO t_ai_conversation(id,user_id,title,mode) VALUES(?,?,'test','AGENT')",conversation,user);
        AgentRunStore store=new AgentRunStore(jdbc,new ObjectMapper().findAndRegisterModules());
        AgentRun run=new AgentRun();run.setUserId(user);run.setConversationId(conversation);run.setMessage("test");run.setModel("test");run.setDeadline(LocalDateTime.now().plusMinutes(1));
        try {
            store.create(run);assertFalse(store.shouldStop(run.getId()));
            run.setStatus("RUNNING");run.setInputTokens(10);assertTrue(store.save(run));
            jdbc.update("UPDATE t_ai_agent_run SET deadline=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 SECOND) WHERE id=?",run.getId());
            store.expire();assertEquals("TIMED_OUT",store.get(run.getId(),user).getStatus());
            assertTrue(store.shouldStop(run.getId()));
            run.setStatus("TIMED_OUT");run.setOutputTokens(5);store.finish(run);store.finish(run);
            assertEquals(15,store.get(run.getId(),user).totalTokens());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_usage_log WHERE conversation_id=?",Integer.class,conversation));
            assertEquals(run.getId(),store.findRequest(run.getRequestId(),user).orElseThrow().getId());
        } finally {
            jdbc.update("DELETE FROM t_ai_usage_log WHERE conversation_id=?",conversation);
            jdbc.update("DELETE FROM t_ai_message WHERE conversation_id=?",conversation);
            jdbc.update("DELETE FROM t_ai_agent_run WHERE conversation_id=?",conversation);
            jdbc.update("DELETE FROM t_ai_conversation WHERE id=?",conversation);
        }
    }
}
