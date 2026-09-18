package com.ff.mycloud.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.model.ModelRegistry;
import com.ff.mycloud.ai.observability.LangfuseChatModelListener;
import com.ff.mycloud.ai.observability.LangfuseProperties;
import com.ff.mycloud.ai.retrieval.query.SearchChatAssistant;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class AIConfig {

    private final AIProperties properties;

    public AIConfig(AIProperties properties) {
        this.properties = properties;
    }

    // ChatModel / StreamingChatModel 由 ModelRegistry 统一管理
    // 不再单独定义 Bean，通过 modelRegistry.getChatModel(name) / getStreamingModel(name) 获取

    @Bean
    EmbeddingModel embeddingModel() {
        String baseUrl = properties.getEmbeddingBaseUrl() != null
                ? properties.getEmbeddingBaseUrl()
                : properties.getBaseUrl();
        String apiKey = properties.getEmbeddingApiKey() != null
                ? properties.getEmbeddingApiKey()
                : properties.getApiKey();
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(properties.getEmbeddingModel())
                .dimensions(properties.getEmbeddingDimensions())
                .timeout(properties.getTimeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(PgVectorProperties pg) {
        return PgVectorEmbeddingStore.builder()
                .host(pg.getHost())
                .port(pg.getPort())
                .user(pg.getUser())
                .password(pg.getPassword())
                .database(pg.getDatabase())
                .table(pg.getTable())
                .dimension(properties.getEmbeddingDimensions())
                .createTable(pg.isCreateTable())
                .build();
    }

    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new OpenAiTokenCountEstimator("gpt-3.5-turbo");
    }

    @Bean
    DocumentSplitter documentSplitter(TokenCountEstimator tokenCountEstimator) {
        return DocumentSplitters.recursive(400, 80, tokenCountEstimator);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "ai.web-search-enabled", havingValue = "true", matchIfMissing = false)
    WebSearchEngine webSearchEngine() {
        return TavilyWebSearchEngine.builder()
                .apiKey(properties.getTavilyApiKey())
                .build();
    }

    @Bean
    SearchChatAssistant searchChatAssistant(ModelRegistry modelRegistry) {
        return AiServices.builder(SearchChatAssistant.class)
                .chatModel(modelRegistry.getChatModel(null))
                .build();
    }


    @Bean
    ChatModelListener chatModelListener(MeterRegistry meterRegistry) {
        return new ChatModelListener() {

            private static final Logger log = LoggerFactory.getLogger(ChatModelListener.class);

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestContext.attributes().put("start", System.currentTimeMillis());
                log.info("AI request: model={}, messages={}",
                        requestContext.chatRequest().modelName(),
                        requestContext.chatRequest().messages().size());
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                long start = (long) responseContext.attributes().getOrDefault("start", System.currentTimeMillis());
                long latency = System.currentTimeMillis() - start;
                String model = responseContext.chatRequest().modelName();

                // 延迟指标
                Timer.builder("ai.chat.latency")
                        .tag("model", model)
                        .description("AI chat latency")
                        .register(meterRegistry)
                        .record(latency, TimeUnit.MILLISECONDS);

                // Token 用量
                var usage = responseContext.chatResponse().tokenUsage();
                if (usage == null || usage.inputTokenCount() == null || usage.outputTokenCount() == null) {
                    meterRegistry.counter("ai.chat.usage.missing", "model", model).increment();
                    return;
                }
                meterRegistry.counter("ai.chat.tokens.input", "model", model)
                        .increment(usage.inputTokenCount());
                meterRegistry.counter("ai.chat.tokens.output", "model", model)
                        .increment(usage.outputTokenCount());
                meterRegistry.counter("ai.chat.calls", "model", model).increment();

                log.info("AI response: model={}, latency={}ms, input={} output={} tokens",
                        model, latency, usage.inputTokenCount(), usage.outputTokenCount());
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                String model = errorContext.chatRequest().modelName();
                meterRegistry.counter("ai.chat.errors", "model", model).increment();
                log.error("AI模型调用失败: model={}", model, errorContext.error());
            }
        };
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="langfuse.enabled", havingValue="true")
    LangfuseChatModelListener langfuseChatModelListener(LangfuseProperties props, ObjectMapper objectMapper) {
        return new LangfuseChatModelListener(props, objectMapper);
    }
}
