package com.ff.mycloud.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Versioned disposable cache: a late writer cannot overwrite a newer conversation. */
@Component
public class ConversationMemoryCache {
    private static final Logger log=LoggerFactory.getLogger(ConversationMemoryCache.class);
    private final RedissonClient redis;
    private final ObjectMapper json;
    private final ChatMemoryProperties config;
    public ConversationMemoryCache(RedissonClient redis,ObjectMapper json,ChatMemoryProperties config) { this.redis=redis;this.json=json;this.config=config; }
    String key(long user,long conversation,long version) { return "ai:memory:v1:"+user+":"+conversation+":"+version; }
    public ConversationMemoryRepository.Snapshot get(ConversationMemoryRepository.Turn turn) {
        try {
            String raw=redis.<String>getBucket(key(turn.userId(),turn.conversationId(),turn.state().version())).get();
            if(raw==null || raw.length()>1000000) return null;
            var snapshot=json.readValue(raw,ConversationMemoryRepository.Snapshot.class);
            if(snapshot.version()!=turn.state().version() || snapshot.throughId()!=turn.state().throughId() || !java.util.Objects.equals(snapshot.summary(),turn.state().summary()) || snapshot.entries()==null || snapshot.entries().stream().anyMatch(e->e.id()>=turn.userMessageId())) return null;
            if(snapshot.entries().size()>2*(config.getWindowTurns()+1)+1 || (turn.state().historyGap() && !snapshot.historyGap())) return null;
            long previous=snapshot.throughId();
            for(var entry:snapshot.entries()) {
                if(entry.id()<=previous || !java.util.Set.of("USER","ASSISTANT").contains(entry.role()) || entry.content()==null || entry.content().isBlank()) return null;
                previous=entry.id();
            }
            return snapshot;
        } catch(Exception e) { log.warn("Chat memory cache unavailable; using database: conversationId={}",turn.conversationId());return null; }
    }
    public void put(long user,long conversation,ConversationMemoryRepository.Snapshot snapshot) {
        try { redis.<String>getBucket(key(user,conversation,snapshot.version())).set(json.writeValueAsString(snapshot),Duration.ofSeconds(config.getCacheTtlSeconds())); }
        catch(Exception e) { log.warn("Chat memory cache update skipped: conversationId={}",conversation); }
    }
}
