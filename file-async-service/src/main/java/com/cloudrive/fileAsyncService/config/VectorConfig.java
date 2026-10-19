package com.cloudrive.fileAsyncService.config;

import com.cloudrive.fileAsyncService.config.properties.EmbeddingProperties;
import com.cloudrive.fileAsyncService.config.properties.PgVectorProperties;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量相关 Bean（只保留异步服务需要的四类）
 * <p>
 * 对话模型、工具、Agent 等 Bean 不迁：异步服务只做索引写入，不做问答。
 * </p>
 */
@Configuration
public class VectorConfig {

    private final EmbeddingProperties properties;
    private final PgVectorProperties pgVectorProperties;

    public VectorConfig(EmbeddingProperties properties, PgVectorProperties pgVectorProperties) {
        this.properties = properties;
        this.pgVectorProperties = pgVectorProperties;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        String apiKey = isBlank(properties.getEmbeddingApiKey())
                ? properties.getApiKey()
                : properties.getEmbeddingApiKey();
        String baseUrl = isBlank(properties.getEmbeddingBaseUrl())
                ? properties.getBaseUrl()
                : properties.getEmbeddingBaseUrl();

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
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(pgVectorProperties.getHost())
                .port(pgVectorProperties.getPort())
                .user(pgVectorProperties.getUser())
                .password(pgVectorProperties.getPassword())
                .database(pgVectorProperties.getDatabase())
                .table(pgVectorProperties.getTable())
                .dimension(properties.getEmbeddingDimensions())
                .createTable(pgVectorProperties.isCreateTable())
                .build();
    }

    @Bean
    public TokenCountEstimator tokenCountEstimator() {
        return new OpenAiTokenCountEstimator("gpt-3.5-turbo");
    }

    @Bean
    public DocumentSplitter documentSplitter(TokenCountEstimator tokenCountEstimator) {
        return DocumentSplitters.recursive(400, 80, tokenCountEstimator);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
