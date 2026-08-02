package com.ff.mycloud.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.agent.AgentOrchestrator;
import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.entity.AiMessage;
import com.ff.mycloud.ai.service.AiChatService;
import com.ff.mycloud.ai.service.ChatAssistant;
import com.ff.mycloud.ai.service.SimpleChatAssistant;
import com.ff.mycloud.ai.tool.CloudDriveTools;
import com.ff.mycloud.ai.config.ModelRegistry;
import com.ff.mycloud.ai.tool.WebSearchTool;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.data.message.UserMessage;
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
import dev.langchain4j.web.search.WebSearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;
    private final ModelRegistry modelRegistry;
    private final FileInfoMapperService fileInfoService;
    private final FileSearchHandler fileSearchHandler;
    private final UserCenterMapperService userCenterService;
    private final FileRecentAccess fileRecentAccess;
    private final FileMapper fileMapper;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore embeddingStore;
    private final DbChatMemoryStore chatMemoryStore;
    private final WebSearchEngine webSearchEngine;
    private final AgentOrchestrator agentOrchestrator;

    public AiChatServiceImpl(AiConversationMapper conversationMapper,
                             AiMessageMapper aiMessageMapper,
                             ObjectMapper objectMapper,
                             ModelRegistry modelRegistry,
                             FileInfoMapperService fileInfoService,
                             FileSearchHandler fileSearchHandler,
                             UserCenterMapperService userCenterService,
                             FileRecentAccess fileRecentAccess,
                             FileMapper fileMapper,
                             EmbeddingModel embeddingModel,
                             EmbeddingStore embeddingStore,
                             DbChatMemoryStore chatMemoryStore,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             WebSearchEngine webSearchEngine,
                             AgentOrchestrator agentOrchestrator) {
        this.conversationMapper = conversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
        this.modelRegistry = modelRegistry;
        this.fileInfoService = fileInfoService;
        this.fileSearchHandler = fileSearchHandler;
        this.userCenterService = userCenterService;
        this.fileRecentAccess = fileRecentAccess;
        this.fileMapper = fileMapper;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMemoryStore = chatMemoryStore;
        this.webSearchEngine = webSearchEngine;
        this.agentOrchestrator = agentOrchestrator;
    }

    @Override
    public SseEmitter sendMessage(ChatRequest request) {
        Long userId = UserContext.getCurrentUserId();
        String userMessage = request.getMessage();
        Long conversationId = request.getConversationId();

        // ===== 模型选择 =====
        String modelName = request.getModel() != null
                ? request.getModel()
                : modelRegistry.getDefaultModelName();
        OpenAiStreamingChatModel streamingModel = modelRegistry.getStreamingModel(modelName);
        OpenAiChatModel chatModel = modelRegistry.getChatModel(modelName);

        // ===== 会话校验/创建 =====
        final AiConversation conv;
        if (conversationId == null) {
            AiConversation newConv = new AiConversation();
            newConv.setUserId(userId);
            newConv.setTitle(userMessage.length() > 50
                    ? userMessage.substring(0, 50) + "..."
                    : userMessage);
            newConv.setModel(modelName);
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

        // ===== 普通聊天：SimpleChatAssistant，仅搜索 + RAG，无文件操作工具 =====
        List<Object> chatTools = new ArrayList<>();
        if (webSearchEngine != null) {
            chatTools.add(new WebSearchTool(webSearchEngine));
        }
        SimpleChatAssistant chatAssistant = AiServices.builder(SimpleChatAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .contentRetriever(retriever)
                .chatMemory(chatMemory)
                .tools(chatTools)
                .build();

        // ===== SseEmitter =====
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> System.out.println("SSE连接正常关闭"));
        emitter.onError(ex -> System.out.println("SSE异常: " + ex.getMessage()));
        emitter.onTimeout(() -> System.out.println("SSE超时"));

        // ===== 先添加干净的 UserMessage，这样 RAG 增强版会被 updateMessages 去重 =====
        chatMemory.add(new UserMessage(userMessage));

        StringBuilder fullResponse = new StringBuilder();

        // Token 用量 — lambda 内赋值，start() 返回后写入 DB
        final TokenUsage[] tokenHolder = new TokenUsage[1];

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
                TokenUsage usage = response.tokenUsage();
                tokenHolder[0] = usage;

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

                int in = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
                int out = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
                String doneData = objectMapper.writeValueAsString(Map.of(
                        "conversationId", finalConversationId,
                        "status", "done",
                        "model", modelName,
                        "usage", Map.of(
                                "inputTokens", in,
                                "outputTokens", out,
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

        // 消息已由 ChatMemoryStore.updateMessages() 持久化，补写 token 用量
        TokenUsage usage = tokenHolder[0];
        if (usage != null) {
            Integer in = usage.inputTokenCount();
            Integer out = usage.outputTokenCount();
            if (in != null || out != null) {
                Long lastId = aiMessageMapper.findLastAssistantId(finalConversationId);
                if (lastId != null) {
                    aiMessageMapper.updateTokens(lastId, in, out);
                }
            }
        }

        return emitter;
    }

    @Override
    public SseEmitter sendAgentMessage(ChatRequest request) {
        return agentOrchestrator.execute(request);
    }

    private String generateTitle(String userMessage, String aiResponse) {
        String prompt = String.format(
                "根据以下对话内容，生成一个简短的会话标题（不超过15个字），只返回标题，不要其他内容：\n用户：%s\nAI：%s",
                userMessage,
                aiResponse.length() > 200 ? aiResponse.substring(0, 200) : aiResponse
        );
        try {
            String title = modelRegistry.getChatModel(null).chat(prompt);
            return title != null ? title.trim().replace("\"", "") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
