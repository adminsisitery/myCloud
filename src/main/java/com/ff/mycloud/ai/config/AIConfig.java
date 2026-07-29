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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private final AIProperties properties;

    public AIConfig(AIProperties properties) {
        this.properties = properties;
    }

    @Bean
    OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .build();
    }

    @Bean
    OpenAiStreamingChatModel openAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .build();
    }

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
}
