package com.ff.mycloud.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

/** Immutable message archive. Window eviction never writes or deletes t_ai_message rows. */
@Repository
public class ConversationMemoryRepository {
    public record Entry(long id, String role, String content) {}
    public record State(long version, String summary, long throughId, boolean historyGap) {}
    public record Snapshot(long version, String summary, long throughId, boolean historyGap, List<Entry> entries) {}
    public record Turn(String id, long userId, long conversationId, long userMessageId, State state) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ChatMemoryProperties config;
    public ConversationMemoryRepository(JdbcTemplate jdbc, PlatformTransactionManager manager, ChatMemoryProperties config) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.config=config;
    }

    public void requireOwner(long userId, long conversationId) {
        if (jdbc.queryForList("SELECT id FROM t_ai_conversation WHERE id=? AND user_id=? AND (mode='CHAT' OR mode IS NULL)",conversationId,userId).isEmpty())
            throw new ResponseStatusException(NOT_FOUND,"聊天会话不存在");
    }
    private void lockConversation(long userId,long conversationId) {
        if (jdbc.queryForList("SELECT id FROM t_ai_conversation WHERE id=? AND user_id=? AND (mode='CHAT' OR mode IS NULL) FOR UPDATE",conversationId,userId).isEmpty())
            throw new ResponseStatusException(NOT_FOUND,"聊天会话不存在");
    }
    public Turn open(long userId,Long conversationId,String requestId,String input,String model) {
        return tx.execute(status -> {
            long id;
            if(conversationId!=null) id=conversationId;
            else {
                var key=new GeneratedKeyHolder();
                jdbc.update(c->{var p=c.prepareStatement("INSERT INTO t_ai_conversation(user_id,title,model,mode,count) VALUES(?,?,?,'CHAT',0)",new String[]{"id"});
                    p.setLong(1,userId);p.setString(2,input.substring(0,Math.min(50,input.length())));p.setString(3,model);return p;},key);
                id=Objects.requireNonNull(key.getKey()).longValue();
            }
            return begin(userId,id,requestId,input);
        });
    }
    public Turn begin(long userId,long conversationId,String requestId,String input) {
        return tx.execute(status -> {
            lockConversation(userId,conversationId);
            if(jdbc.queryForList("SELECT conversation_id FROM t_ai_chat_memory WHERE conversation_id=?",conversationId).isEmpty())
                jdbc.update("INSERT INTO t_ai_chat_memory(conversation_id,user_id) VALUES(?,?)",conversationId,userId);
            var rows=jdbc.queryForList("SELECT lease_id,(lease_until>CURRENT_TIMESTAMP) AS live FROM t_ai_chat_memory WHERE conversation_id=? FOR UPDATE",conversationId);
            String lease=(String)rows.getFirst().get("lease_id");
            Object live=rows.getFirst().get("live");
            if(lease!=null && (Boolean.TRUE.equals(live) || live instanceof Number n && n.intValue()==1))
                throw new ResponseStatusException(CONFLICT,"当前会话正在回答，请完成或停止后再发送");
            if(lease!=null) {
                jdbc.update("UPDATE t_ai_chat_turn SET status='TIMED_OUT',error_code='TURN_EXPIRED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'",lease);
                jdbc.update("UPDATE t_ai_chat_memory SET version=version+1 WHERE conversation_id=?",conversationId);
            }
            String id=UUID.randomUUID().toString();
            long userMessageId=append(conversationId,"USER",input,null,null);
            jdbc.update("INSERT INTO t_ai_chat_turn(id,request_id,user_id,conversation_id,user_message_id,status) VALUES(?,?,?,?,?,'RUNNING')",
                    id,requestId==null?id:requestId,userId,conversationId,userMessageId);
            jdbc.update("UPDATE t_ai_chat_memory SET lease_id=?,lease_until=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP) WHERE conversation_id=? AND user_id=?",id,config.getLeaseSeconds(),conversationId,userId);
            return new Turn(id,userId,conversationId,userMessageId,state(userId,conversationId));
        });
    }
    public State state(long userId,long conversationId) {
        return jdbc.queryForObject("SELECT version,summary,through_message_id,history_gap FROM t_ai_chat_memory WHERE conversation_id=? AND user_id=?",
                (r,n)->new State(r.getLong(1),r.getString(2),r.getLong(3),r.getBoolean(4)),conversationId,userId);
    }
    public void requireActive(Turn turn) {
        requireOwner(turn.userId(),turn.conversationId());
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_chat_memory WHERE conversation_id=? AND user_id=? AND lease_id=? AND lease_until>CURRENT_TIMESTAMP",Integer.class,turn.conversationId(),turn.userId(),turn.id());
        if(n==null || n!=1) throw new ResponseStatusException(CONFLICT,"本轮已结束或超时");
    }
    public Snapshot load(Turn turn) { return load(turn.userId(),turn.conversationId(),turn.userMessageId()-1); }
    private Snapshot load(long userId,long conversationId,long upperId) {
        requireOwner(userId,conversationId);
        State state=state(userId,conversationId);
        var entries=new ArrayList<>(jdbc.query("SELECT id,role,content FROM t_ai_message WHERE conversation_id=? AND id>? AND id<=? AND role IN ('USER','ASSISTANT') ORDER BY id DESC LIMIT ?",
                (r,n)->new Entry(r.getLong(1),r.getString(2),r.getString(3)),conversationId,state.throughId(),upperId,2*(config.getWindowTurns()+1)+1));
        Collections.reverse(entries);
        while(!entries.isEmpty() && !"USER".equals(entries.getFirst().role())) entries.removeFirst();
        boolean gap=state.historyGap();
        if(!entries.isEmpty()) gap|=Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM t_ai_message WHERE conversation_id=? AND id>? AND id<?",Boolean.class,conversationId,state.throughId(),entries.getFirst().id()));
        return new Snapshot(state.version(),state.summary(),state.throughId(),gap,List.copyOf(entries));
    }
    public State saveSummary(Turn turn,Snapshot original,String summary,long throughId) {
        return tx.execute(status -> {
            lockConversation(turn.userId(),turn.conversationId()); requireActive(turn);
            int changed=jdbc.update("UPDATE t_ai_chat_memory SET summary=?,through_message_id=?,history_gap=?,version=version+1 WHERE conversation_id=? AND user_id=? AND lease_id=? AND version=?",
                    summary,throughId,original.historyGap(),turn.conversationId(),turn.userId(),turn.id(),original.version());
            if(changed!=1) throw new ResponseStatusException(CONFLICT,"记忆版本已变化，请重试");
            return state(turn.userId(),turn.conversationId());
        });
    }
    public Snapshot complete(Turn turn,String answer,List<ChatMessage> messages,String model,int inputTokens,int outputTokens,long latencyMs) {
        return tx.execute(status -> {
            lockConversation(turn.userId(),turn.conversationId()); requireActive(turn);
            long assistantId=append(turn.conversationId(),"ASSISTANT",answer,inputTokens,outputTokens);
            jdbc.update("UPDATE t_ai_chat_turn SET assistant_message_id=?,messages=?,status='SUCCEEDED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'",assistantId,ChatMessageJsonCodec.toJson(messages),turn.id());
            jdbc.update("UPDATE t_ai_chat_memory SET lease_id=NULL,lease_until=NULL,version=version+1 WHERE conversation_id=? AND lease_id=?",turn.conversationId(),turn.id());
            jdbc.update("UPDATE t_ai_conversation SET count=COALESCE(count,0)+1,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",turn.conversationId(),turn.userId());
            usage(turn,model,"chat",inputTokens,outputTokens,latencyMs);
            return load(turn.userId(),turn.conversationId(),assistantId);
        });
    }
    public void fail(Turn turn,String code,List<ChatMessage> messages) {
        fail(turn,code,messages,null,0,0,0);
    }
    public void fail(Turn turn,String code,List<ChatMessage> messages,String model,int input,int output,long latency) {
        tx.executeWithoutResult(status -> {
            // Use the same lock order as begin/complete so a late callback cannot race a new turn.
            lockConversation(turn.userId(),turn.conversationId());
            int changed=jdbc.update("UPDATE t_ai_chat_turn SET status='FAILED',error_code=?,messages=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='RUNNING'",code,ChatMessageJsonCodec.toJson(messages),turn.id(),turn.userId());
            if(changed==1 && model!=null && input+output>0) usage(turn,model,"chat_failed",input,output,latency);
            jdbc.update("UPDATE t_ai_chat_memory SET lease_id=NULL,lease_until=NULL,version=version+1 WHERE conversation_id=? AND user_id=? AND lease_id=?",turn.conversationId(),turn.userId(),turn.id());
        });
    }
    public void checkpoint(Turn turn,List<ChatMessage> messages) {
        requireActive(turn);
        jdbc.update("UPDATE t_ai_chat_turn SET messages=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='RUNNING'",ChatMessageJsonCodec.toJson(messages),turn.id());
    }
    public void usage(Turn turn,String model,String scene,int input,int output,long latency) {
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO t_ai_usage_log(user_id,conversation_id,model,scene,prompt_tokens,completion_tokens,total_tokens,latency_ms,created_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                turn.userId(),turn.conversationId(),model,scene,input,output,input+output,latency);
            jdbc.update("UPDATE t_ai_conversation SET total_tokens=COALESCE(total_tokens,0)+? WHERE id=? AND user_id=?",input+output,turn.conversationId(),turn.userId());
        });
    }
    public List<Entry> search(Turn turn,String keyword,long beforeId) {
        requireActive(turn);
        String escaped=keyword.replace("!","!!").replace("%","!%").replace("_","!_");
        return jdbc.query("SELECT id,role,SUBSTRING(content,1,1200) FROM t_ai_message WHERE conversation_id=? AND id<? AND content LIKE ? ESCAPE '!' ORDER BY id DESC LIMIT 4",
                (r,n)->new Entry(r.getLong(1),r.getString(2),r.getString(3)),turn.conversationId(),Math.min(beforeId,turn.userMessageId()),"%"+escaped+"%");
    }
    public List<Entry> read(Turn turn,long messageId,int offset) {
        requireActive(turn);
        return jdbc.query("SELECT id,role,SUBSTRING(content,?,2000) FROM t_ai_message WHERE conversation_id=? AND id=? AND id<?",
                (r,n)->new Entry(r.getLong(1),r.getString(2),r.getString(3)),offset+1,turn.conversationId(),messageId,turn.userMessageId());
    }
    private long append(long conversationId,String role,String content,Integer input,Integer output) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c -> {
            var p=c.prepareStatement("INSERT INTO t_ai_message(conversation_id,role,content,input_tokens,output_tokens,create_time) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",new String[]{"id"});
            p.setLong(1,conversationId);p.setString(2,role);p.setString(3,content);p.setObject(4,input);p.setObject(5,output);return p;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
