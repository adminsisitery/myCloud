package com.ff.mycloud.ai.memory;

import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.model.entity.AiMessages;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MySQL 的 ChatMemoryStore 实现。
 * <p>
 * getMessages：从 DB 加载历史消息，供 MessageWindowChatMemory 恢复上下文。
 * updateMessages：空实现，持久化由 Service 层手动完成，避免窗口截断导致历史丢失。
 * deleteMessages：空实现，删除由 Service 层调用 Mapper 完成。
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
        if (memoryId == null) {
            return List.of();
        }
        Long convId;
        try {
            convId = Long.parseLong(String.valueOf(memoryId));
        } catch (NumberFormatException e) {
            return List.of();
        }

        List<AiMessages> dbMessages = aiMessageMapper.findByMsgId(convId);
        if (dbMessages == null || dbMessages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (AiMessages msg : dbMessages) {
            result.add(switch (msg.getRole()) {
                case "USER" -> UserMessage.from(msg.getContent());
                case "ASSISTANT" -> AiMessage.from(msg.getContent());
                default -> SystemMessage.from(msg.getContent());
            });
        }
        return result;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 持久化由 Service 层手动完成，此处不操作，避免 MessageWindowChatMemory 截断历史
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 由 Service 层调用 Mapper 完成
    }
}
