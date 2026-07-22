package com.ff.mycloud.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.entity.AiMessages;
import com.ff.mycloud.ai.service.AiChatService;
import com.ff.mycloud.ai.service.ChatAssistant;
import com.ff.mycloud.common.util.UserContext;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 聊天服务实现。
 * <p>
 * 使用 LangChain4j AiServices（{@link ChatAssistant}）处理对话编排，
 * 将 TokenStream 桥接到 SseEmitter 实现流式输出。
 * AiServices 自动注入 System Prompt、管理 ChatMemory、加载历史上下文。
 * </p>
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatAssistant chatAssistant;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;

    public AiChatServiceImpl(ChatAssistant chatAssistant,
                             AiConversationMapper conversationMapper,
                             AiMessageMapper aiMessageMapper,
                             ObjectMapper objectMapper) {
        this.chatAssistant = chatAssistant;
        this.conversationMapper = conversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter sendMessage(ChatRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();

        // ===== 会话校验/创建 =====
        if (conversationId == null) {
            AiConversation conv = new AiConversation();
            conv.setUserId(userId);
            conv.setTitle(userMessage.length() > 50
                    ? userMessage.substring(0, 50) + "..."
                    : userMessage);
            conv.setModel("deepseek-chat");
            conversationMapper.insert(conv);
            conversationId = conv.getId();
        } else {
            AiConversation conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                throw new IllegalArgumentException("会话不存在");
            }
            if (!conv.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无权访问该会话");
            }
        }

        final Long finalConversationId = conversationId;

        // ===== SseEmitter =====
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.onCompletion(() -> System.out.println("SSE连接正常关闭"));
        emitter.onError(ex -> System.out.println("SSE异常: " + ex.getMessage()));
        emitter.onTimeout(() -> System.out.println("SSE超时"));

        StringBuilder fullResponse = new StringBuilder();

        // ===== AiServices 调用 + TokenStream → SseEmitter 桥接 =====
        TokenStream tokenStream = chatAssistant.chat(finalConversationId, userMessage);

        tokenStream.onPartialResponse(token -> {
            fullResponse.append(token);
            try {
                emitter.send(SseEmitter.event().name("chunk").data(token));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        tokenStream.onCompleteResponse(response -> {
            try {
                String assistantContent = fullResponse.toString();

                // 手动持久化用户和 AI 消息（保存完整历史，不依赖 ChatMemoryStore 的截断窗口）
                AiMessages userMsg = new AiMessages();
                userMsg.setConversationId(finalConversationId);
                userMsg.setRole("USER");
                userMsg.setContent(userMessage);
                aiMessageMapper.insert(userMsg);

                AiMessages assistantMsg = new AiMessages();
                assistantMsg.setConversationId(finalConversationId);
                assistantMsg.setRole("ASSISTANT");
                assistantMsg.setContent(assistantContent);
                aiMessageMapper.insert(assistantMsg);

                // 更新会话时间
                AiConversation updateConv = new AiConversation();
                updateConv.setId(finalConversationId);
                updateConv.setUpdateTime(LocalDateTime.now());
                conversationMapper.updateById(updateConv);

                String doneData = objectMapper.writeValueAsString(Map.of(
                        "conversationId", finalConversationId,
                        "status", "done"
                ));
                emitter.send(SseEmitter.event().name("done").data(doneData));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        tokenStream.onError(throwable -> {
            try {
                String errorData = objectMapper.writeValueAsString(Map.of(
                        "error", throwable.getMessage() != null
                                ? throwable.getMessage()
                                : "AI service error"
                ));
                emitter.send(SseEmitter.event().name("error").data(errorData));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
