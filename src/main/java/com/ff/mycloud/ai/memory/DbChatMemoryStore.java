package com.ff.mycloud.ai.memory;

import com.ff.mycloud.ai.mapper.AiMessageMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MySQL 的 ChatMemory 持久化存储。
 * <p>
 * getMessages: 从 t_ai_message 加载历史 → 转为 LC4j ChatMessage（带正确 role）
 * updateMessages: no-op（消息写入在 AiChatServiceImpl.onCompleteResponse 中完成）
 * deleteMessages: 删除会话下所有消息
 * </p>
 */
@Component
public class DbChatMemoryStore implements ChatMemoryStore {

    private final AiMessageMapper aiMessageMapper;

    public DbChatMemoryStore(AiMessageMapper aiMessageMapper) {
        this.aiMessageMapper = aiMessageMapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long conversationId = ((Number) memoryId).longValue();
        List<com.ff.mycloud.ai.model.entity.AiMessage> entities = aiMessageMapper.findByMsgId(conversationId);

        List<ChatMessage> messages = new ArrayList<>();
        for (com.ff.mycloud.ai.model.entity.AiMessage entity : entities) {
            if ("USER".equals(entity.getRole())) {
                messages.add(new UserMessage(entity.getContent()));
            } else if ("ASSISTANT".equals(entity.getRole())) {
                messages.add(new dev.langchain4j.data.message.AiMessage(entity.getContent()));
            }
        }
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // no-op: 持久化在 AiChatServiceImpl.onCompleteResponse 中统一处理
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Long conversationId = ((Number) memoryId).longValue();
        aiMessageMapper.deleteByCId(conversationId);
    }
}
