package com.ff.mycloud.ai.memory;

import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.model.entity.AiConversation;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 MySQL 的 ChatMemory 持久化存储。
 * <p>
 * 参照 LC4j 官方示例，将完整消息列表（含工具调用/结果）序列化为 JSON
 * 保存在 {@code t_ai_conversation.memory_state}，{@code getMessages()}
 * 直接从 JSON 反序列化返回。这样工具循环中模型能看到自己的工具调用历史。
 * <p>
 * {@code t_ai_message} 表单独维护 USER + ASSISTANT 行，仅供前端展示。
 */
@Component
public class DbChatMemoryStore implements ChatMemoryStore {

    private final AiMessageMapper aiMessageMapper;
    private final AiConversationMapper conversationMapper;

    /** 每个会话最后持久化的消息列表哈希（避免无变更时重复写 DB） */
    private final Map<Long, String> lastSyncHash = new ConcurrentHashMap<>();

    public DbChatMemoryStore(AiMessageMapper aiMessageMapper,
                             AiConversationMapper conversationMapper) {
        this.aiMessageMapper = aiMessageMapper;
        this.conversationMapper = conversationMapper;
    }

    // ==================== 读 ====================

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long convId = ((Number) memoryId).longValue();
        AiConversation conv = conversationMapper.selectById(convId);
        if (conv == null || conv.getMemoryState() == null) {
            // 兼容旧数据：从 t_ai_message 重建
            return loadFromMessageRows(convId);
        }
        return ChatMessageJsonCodec.fromJson(conv.getMemoryState());
    }

    /** 兼容没有 memory_state 的旧会话 */
    private List<ChatMessage> loadFromMessageRows(Long convId) {
        List<com.ff.mycloud.ai.model.entity.AiMessage> entities =
                aiMessageMapper.findByMsgId(convId);
        List<ChatMessage> messages = new ArrayList<>();
        if (entities != null) {
            for (com.ff.mycloud.ai.model.entity.AiMessage entity : entities) {
                if ("USER".equals(entity.getRole())) {
                    messages.add(new UserMessage(entity.getContent()));
                } else if ("ASSISTANT".equals(entity.getRole())) {
                    messages.add(new dev.langchain4j.data.message.AiMessage(entity.getContent()));
                }
            }
        }
        return messages;
    }

    // ==================== 写 ====================

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long convId = ((Number) memoryId).longValue();

        // 1. 序列化完整消息列表为 JSON（含工具调用/结果）
        String json = ChatMessageJsonCodec.toJson(messages);

        // 2. 哈希无变更 → 跳过
        String newHash = String.valueOf(json.hashCode());
        String oldHash = lastSyncHash.get(convId);
        if (newHash.equals(oldHash)) {
            return;
        }

        // 3. 更新 conversation.memory_state（核心：完整消息列表）
        AiConversation updateConv = new AiConversation();
        updateConv.setId(convId);
        updateConv.setMemoryState(json);
        conversationMapper.updateById(updateConv);

        // 4. 同步 t_ai_message（侧效应：给前端展示用，只存 USER + ASSISTANT）
        syncMessageRows(convId, messages);

        lastSyncHash.put(convId, newHash);
    }

    /**
     * 同步 t_ai_message 表（差分，只存 USER + ASSISTANT 文本）。
     * 这是 getMessages() 的辅助存储，前端通过 MessageVO 查询这些行。
     */
    private void syncMessageRows(Long convId, List<ChatMessage> messages) {
        List<com.ff.mycloud.ai.model.entity.AiMessage> current =
                aiMessageMapper.findByMsgId(convId);
        List<MsgEntry> currentEntries = toMsgEntries(current);

        List<MsgEntry> newEntries = toPersistableEntries(messages);

        int match = commonPrefix(currentEntries, newEntries);

        // INSERT 新增（先插后删）
        for (int i = match; i < newEntries.size(); i++) {
            MsgEntry e = newEntries.get(i);
            com.ff.mycloud.ai.model.entity.AiMessage entity =
                    new com.ff.mycloud.ai.model.entity.AiMessage();
            entity.setConversationId(convId);
            entity.setRole(e.role);
            entity.setContent(e.content);
            aiMessageMapper.insert(entity);
        }

        // DELETE 多余（从尾部删）
        for (int i = currentEntries.size() - 1; i >= match; i--) {
            aiMessageMapper.deleteById(current.get(i).getId());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Long convId = ((Number) memoryId).longValue();
        aiMessageMapper.deleteByCId(convId);
        // 清空 memory_state
        AiConversation updateConv = new AiConversation();
        updateConv.setId(convId);
        updateConv.setMemoryState(null);
        conversationMapper.updateById(updateConv);
        lastSyncHash.remove(convId);
    }

    // ==================== 消息提取（仅用于 t_ai_message 同步） ====================

    private List<MsgEntry> toPersistableEntries(List<ChatMessage> messages) {
        List<MsgEntry> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage um) {
                String content = textOfUser(um);
                if (content == null || content.isBlank()) continue;
                if (!result.isEmpty() && "USER".equals(result.get(result.size() - 1).role)) {
                    continue; // 连续 USER → RAG 重复
                }
                result.add(new MsgEntry("USER", content));
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage aim) {
                String content = textOfAi(aim);
                if (content == null || content.isBlank()) continue;
                result.add(new MsgEntry("ASSISTANT", content));
            }
        }
        return result;
    }

    private static List<MsgEntry> toMsgEntries(
            List<com.ff.mycloud.ai.model.entity.AiMessage> entities) {
        List<MsgEntry> result = new ArrayList<>();
        for (com.ff.mycloud.ai.model.entity.AiMessage e : entities) {
            result.add(new MsgEntry(e.getRole(), e.getContent()));
        }
        return result;
    }

    private static int commonPrefix(List<MsgEntry> a, List<MsgEntry> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!a.get(i).equals(b.get(i))) return i;
        }
        return n;
    }

    // ==================== 文本提取 ====================

    private static String textOfUser(UserMessage msg) {
        try {
            String t = msg.singleText();
            if (t != null && !t.isBlank()) return stripRagAugmentation(t);
        } catch (Exception ignored) {}
        try {
            for (dev.langchain4j.data.message.Content c : msg.contents()) {
                if (c instanceof dev.langchain4j.data.message.TextContent tc) {
                    String t = tc.text();
                    if (t != null && !t.isBlank()) return stripRagAugmentation(t);
                }
            }
        } catch (Exception ignored) {}
        return msg.toString();
    }

    private static String stripRagAugmentation(String text) {
        if (text == null || text.length() <= 200) return text;
        int idx = text.indexOf("\n\nAnswer using the following information:");
        if (idx > 0) return text.substring(0, idx).trim();
        idx = text.indexOf("\n\nHere are");
        if (idx > 0) return text.substring(0, idx).trim();
        idx = text.indexOf("\n\nBased on the following");
        if (idx > 0) return text.substring(0, idx).trim();
        return text;
    }

    private static String textOfAi(dev.langchain4j.data.message.AiMessage msg) {
        try {
            String t = msg.text();
            if (t != null && !t.isBlank()) return t;
        } catch (Exception ignored) {}
        return null; // 纯 tool-call，不存到 t_ai_message
    }

    // ==================== 工具 ====================

    private record MsgEntry(String role, String content) {}
}
