package com.ff.mycloud.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.entity.AiMessage;
import com.ff.mycloud.ai.service.AiChatService;
import com.ff.mycloud.ai.service.ChatAssistant;
import com.ff.mycloud.ai.tool.CloudDriveTools;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;
    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingModel;
    private final FileInfoMapperService fileInfoService;
    private final FileSearchHandler fileSearchHandler;
    private final UserCenterMapperService userCenterService;
    private final FileRecentAccess fileRecentAccess;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore embeddingStore;
    private final DbChatMemoryStore chatMemoryStore;

    public AiChatServiceImpl(AiConversationMapper conversationMapper,
                             AiMessageMapper aiMessageMapper,
                             ObjectMapper objectMapper,
                             OpenAiChatModel chatModel,
                             OpenAiStreamingChatModel streamingModel,
                             FileInfoMapperService fileInfoService,
                             FileSearchHandler fileSearchHandler,
                             UserCenterMapperService userCenterService,
                             FileRecentAccess fileRecentAccess,
                             EmbeddingModel embeddingModel,
                             EmbeddingStore embeddingStore,
                             DbChatMemoryStore chatMemoryStore) {
        this.conversationMapper = conversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.fileInfoService = fileInfoService;
        this.fileSearchHandler = fileSearchHandler;
        this.userCenterService = userCenterService;
        this.fileRecentAccess = fileRecentAccess;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public SseEmitter sendMessage(ChatRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();

        // ===== 会话校验/创建 =====
        final AiConversation conv;
        if (conversationId == null) {
            AiConversation newConv = new AiConversation();
            newConv.setUserId(userId);
            newConv.setTitle(userMessage.length() > 50
                    ? userMessage.substring(0, 50) + "..."
                    : userMessage);
            newConv.setModel("deepseek-chat");
            newConv.setCount(0L);
            conversationMapper.insert(newConv);
            conversationId = newConv.getId();
            conv = newConv;
        } else {
            conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                throw new IllegalArgumentException("会话不存在");
            }
            if (!conv.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无权访问该会话");
            }
        }

        final Long finalConversationId = conversationId;
        final boolean isFirstMessage = conv.getCount() == null || conv.getCount() == 0;

        // ===== ChatMemory：从 DB 加载历史，自动管理多轮对话 =====
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(finalConversationId)
                .maxMessages(40)  // ~20 轮对话
                .chatMemoryStore(chatMemoryStore)
                .build();

        // ===== per-request ContentRetriever，userId 过滤，绝对隔离 =====
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(metadataKey("userId").isEqualTo(String.valueOf(userId)))
                .maxResults(5)
                .minScore(0.6)
                .build();

        // ===== 每次请求新建 Tool + AiServices，userId 写死在实例里，绝对隔离 =====
        ChatAssistant chatAssistant = AiServices.builder(ChatAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .contentRetriever(retriever)
                .chatMemory(chatMemory)
                .tools(new CloudDriveTools(fileInfoService, fileSearchHandler, userCenterService, fileRecentAccess, userId))
                .build();

        // ===== SseEmitter =====
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> System.out.println("SSE连接正常关闭"));
        emitter.onError(ex -> System.out.println("SSE异常: " + ex.getMessage()));
        emitter.onTimeout(() -> System.out.println("SSE超时"));

        StringBuilder fullResponse = new StringBuilder();

        TokenStream tokenStream = chatAssistant.chat(userMessage);

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

                // ---------- Token 用量 ----------
                TokenUsage usage = response.tokenUsage();
                Integer inputTokens = usage != null ? usage.inputTokenCount() : null;
                Integer outputTokens = usage != null ? usage.outputTokenCount() : null;

                AiMessage userMsg = new AiMessage();
                userMsg.setConversationId(finalConversationId);
                userMsg.setRole("USER");
                userMsg.setContent(userMessage);
                aiMessageMapper.insert(userMsg);

                AiMessage assistantMsg = new AiMessage();
                assistantMsg.setConversationId(finalConversationId);
                assistantMsg.setRole("ASSISTANT");
                assistantMsg.setContent(assistantContent);
                assistantMsg.setInputTokens(inputTokens);
                assistantMsg.setOutputTokens(outputTokens);
                aiMessageMapper.insert(assistantMsg);

                // 更新会话：计数 + token 累计
                AiConversation updateConv = new AiConversation();
                updateConv.setId(finalConversationId);
                updateConv.setUpdateTime(LocalDateTime.now());
                long newCount = (conv.getCount() == null ? 0 : conv.getCount()) + 1;
                updateConv.setCount(newCount);
                long totalTokens = usage != null ? usage.totalTokenCount() : 0;
                long prevTotal = conv.getTotalTokens() != null ? conv.getTotalTokens() : 0;
                updateConv.setTotalTokens(prevTotal + totalTokens);
                conversationMapper.updateById(updateConv);

                if (isFirstMessage) {
                    String title = generateTitle(userMessage, assistantContent);
                    conversationMapper.update(finalConversationId, title);
                }

                String doneData = objectMapper.writeValueAsString(Map.of(
                        "conversationId", finalConversationId,
                        "status", "done",
                        "usage", Map.of(
                                "inputTokens", inputTokens != null ? inputTokens : 0,
                                "outputTokens", outputTokens != null ? outputTokens : 0,
                                "totalTokens", totalTokens
                        )
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

        tokenStream.start();
        return emitter;
    }

    private String generateTitle(String userMessage, String aiResponse) {
        String prompt = String.format(
                "根据以下对话内容，生成一个简短的会话标题（不超过15个字），只返回标题，不要其他内容：\n用户：%s\nAI：%s",
                userMessage,
                aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse
        );
        try {
            String title = chatModel.chat(prompt);
            return title != null ? title.trim().replace("\"", "") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
