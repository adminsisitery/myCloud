package com.ff.mycloud.ai.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host("47.95.192.221")
                .port(5432)
                .user("postgres")
                .password("2005525a")
                .database("postgres")
                .table("file_segment")
                .dimension(1536)
                .createTable(true)
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
            name = "ai.tavily-api-key", matchIfMissing = false)
    WebSearchEngine webSearchEngine() {
        return TavilyWebSearchEngine.builder()
                .apiKey(properties.getTavilyApiKey())
                .build();
    }
}
