package com.ff.mycloud.ai.memory;

import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import java.util.*;

/** Request-owned memory; current tool requests/results are never evicted or reloaded from DB. */
public class SessionChatMemory implements ChatMemory {
    private final long id;
    private final List<ChatMessage> history;
    private final List<ChatMessage> current=new ArrayList<>();
    private SystemMessage system;
    public SessionChatMemory(long id,List<ChatMessage> history) { this.id=id;this.history=List.copyOf(history); }
    @Override public Object id() { return id; }
    @Override public synchronized void add(ChatMessage message) {
        if(message instanceof SystemMessage s) system=s; else current.add(message);
    }
    @Override public synchronized List<ChatMessage> messages() {
        List<ChatMessage> result=new ArrayList<>();if(system!=null) result.add(system);
        result.addAll(history);result.addAll(current);return result;
    }
    public synchronized List<ChatMessage> archive() { return List.copyOf(current); }
    @Override public synchronized void clear() { current.clear();system=null; }
}
