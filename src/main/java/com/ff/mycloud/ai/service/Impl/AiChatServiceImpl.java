package com.ff.mycloud.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.ai.ContentRetrievers.ESAndPgContentRetriever;
import com.ff.mycloud.ai.cache.AICacheService;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.mapper.AiUsageLogMapper;
import com.ff.mycloud.ai.agent.AgentOrchestrator;
import com.ff.mycloud.ai.config.PromptService;
import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.entity.AiMessage;
import com.ff.mycloud.ai.model.entity.AiUsageLog;
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
import dev.langchain4j.rag.content.retriever.ContentRetriever;
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
    private final AiUsageLogMapper aiUsageLogMapper;
    private final ObjectMapper objectMapper;
    private final ModelRegistry modelRegistry;
    private final PromptService promptService;
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
    private final FileSearchRepository fileSearchRepository;
    private final AICacheService aiCacheService;

    public AiChatServiceImpl(AiConversationMapper conversationMapper,
                             AiMessageMapper aiMessageMapper,
                             AiUsageLogMapper aiUsageLogMapper,
                             ObjectMapper objectMapper,
                             ModelRegistry modelRegistry,
                             PromptService promptService,
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
                             AgentOrchestrator agentOrchestrator,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             FileSearchRepository fileSearchRepository,
                             AICacheService aiCacheService) {
        this.conversationMapper = conversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.aiUsageLogMapper = aiUsageLogMapper;
        this.objectMapper = objectMapper;
        this.modelRegistry = modelRegistry;
        this.promptService = promptService;
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
        this.fileSearchRepository = fileSearchRepository;
        this.aiCacheService = aiCacheService;
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

        // ===== 新会话首条消息：检查 AI 响应缓存 =====
        if (isFirstMessage) {
            String cached = aiCacheService.get(userId, userMessage, modelName);
            if (cached != null) {
                // 缓存命中，直接模拟流式返回
                SseEmitter hitEmitter = new SseEmitter(60_000L);
                new Thread(() -> {
                    try {
                        for (String line : cached.split("(?<=\\n)")) {
                            if (!line.isEmpty()) {
                                hitEmitter.send(SseEmitter.event().name("chunk").data(line));
                                try { Thread.sleep(15); } catch (InterruptedException ignored) {} // 模拟流式节奏
                            }
                        }
                        hitEmitter.send(SseEmitter.event().name("done").data(
                                objectMapper.writeValueAsString(Map.of(
                                        "conversationId", finalConversationId,
                                        "status", "cached"
                                ))));
                        hitEmitter.complete();

                        // 保存缓存内容到消息表
                        AiMessage cachedMsg = new AiMessage();
                        cachedMsg.setConversationId(finalConversationId);
                        cachedMsg.setRole("ASSISTANT");
                        cachedMsg.setContent(cached);
                        aiMessageMapper.insert(cachedMsg);
                    } catch (Exception e) {
                        hitEmitter.completeWithError(e);
                    }
                }).start();
                return hitEmitter;
            }
        }

        // ===== ChatMemory：从 DB 加载历史，自动管理多轮对话 =====
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(finalConversationId)
                .maxMessages(40)  // ~20 轮对话
                .chatMemoryStore(chatMemoryStore)
                .build();

        // ===== per-request ContentRetriever，userId 过滤，绝对隔离 =====
        // ES 可用时走混合检索，否则降级为纯 PGVector
        ContentRetriever retriever;
        if (fileSearchRepository != null) {
            retriever = new ESAndPgContentRetriever(
                    embeddingStore, embeddingModel, fileSearchRepository,
                    userId, 5, 0.6,
                    metadataKey("userId").isEqualTo(String.valueOf(userId)),
                    "chat"
            );
        } else {
            retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .filter(metadataKey("userId").isEqualTo(String.valueOf(userId)))
                    .maxResults(5)
                    .minScore(0.6)
                    .build();
        }

        // ===== 普通聊天：SimpleChatAssistant，仅搜索 + RAG，无文件操作工具 =====
        List<Object> chatTools = new ArrayList<>();
        if (webSearchEngine != null) {
            chatTools.add(new WebSearchTool(webSearchEngine));
        }
        SimpleChatAssistant chatAssistant = AiServices.builder(SimpleChatAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> promptService.getChatPrompt())
                .contentRetriever(retriever)
                .chatMemory(chatMemory)
                .tools(chatTools)
                .build();

        // ===== SseEmitter =====
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> System.out.println("SSE连接正常关闭"));
        emitter.onError(ex -> System.out.println("SSE异常: " + ex.getMessage()));
        emitter.onTimeout(() -> System.out.println("SSE超时"));

        // ===== 计时开始 =====
        final long startTime = System.currentTimeMillis();

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
            } catch (Exception ignored) {
                // emitter 可能暂时不可用，不关闭，继续尝试后续 chunk
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
                    // AI 响应缓存：首条消息的结果缓存 15 分钟
                    if (assistantContent != null && assistantContent.length() >= 10) {
                        aiCacheService.put(userId, userMessage, modelName, assistantContent);
                    }
                }

                int in = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
                int out = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;

                // ===== 写入 Token 用量明细日志 =====
                try {
                    AiUsageLog usageLog = AiUsageLog.builder()
                            .userId(userId)
                            .conversationId(finalConversationId)
                            .model(modelName)
                            .scene("chat")
                            .promptTokens(in)
                            .completionTokens(out)
                            .totalTokens((int) totalTokens)
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .createdAt(LocalDateTime.now())
                            .build();
                    aiUsageLogMapper.insert(usageLog);
                } catch (Exception logEx) {
                    // 日志写入失败不影响主流程
                    System.err.println("Token usage log insert failed: " + logEx.getMessage());
                }

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
            } catch (Exception e) {
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
            } catch (Exception e) {
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
