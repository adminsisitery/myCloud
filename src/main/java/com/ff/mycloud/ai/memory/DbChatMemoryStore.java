package com.ff.mycloud.ai.memory;

import com.ff.mycloud.ai.persistence.entity.AiConversation;
import com.ff.mycloud.ai.persistence.mapper.AiConversationMapper;
import com.ff.mycloud.ai.persistence.mapper.AiMessageMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.*;

import org.springframework.stereotype.Component;

/**
 * Legacy memory snapshot adapter. Window eviction must never delete the UI history.
 */
@Component
public class DbChatMemoryStore implements ChatMemoryStore {
    private final AiMessageMapper messages;
    private final AiConversationMapper conversations;

    public DbChatMemoryStore(AiMessageMapper messages, AiConversationMapper conversations) {
        this.messages = messages;
        this.conversations = conversations;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        long id = ((Number) memoryId).longValue();
        var conversation = conversations.selectById(id);
        if (conversation != null && conversation.getMemoryState() != null)
            return ChatMessageJsonCodec.fromJson(conversation.getMemoryState());
        var rows = messages.findByMsgId(id);
        List<ChatMessage> result = new ArrayList<>();
        if (rows != null) for (var row : rows) {
            if ("USER".equals(row.getRole())) result.add(UserMessage.from(row.getContent()));
            else if ("ASSISTANT".equals(row.getRole()))
                result.add(dev.langchain4j.data.message.AiMessage.from(row.getContent()));
        }
        return result;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> window) {
        var update = new AiConversation();
        update.setId(((Number) memoryId).longValue());
        update.setMemoryState(ChatMessageJsonCodec.toJson(window));
        conversations.updateById(update);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        updateMessages(memoryId, List.of());
    }
}
