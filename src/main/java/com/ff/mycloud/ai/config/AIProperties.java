package com.ff.mycloud.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
@Data
public class AIProperties {
    /** Chat API Key */
    private String apiKey;
    /** Chat API 地址 */
    private String baseUrl = "https://api.deepseek.com";
    /** Chat 模型名 */
    private String model = "deepseek-chat";
    /** Embedding API 地址（可与 chat 不同） */
    private String embeddingBaseUrl;
    /** Embedding API Key（默认复用 chat 的 key） */
    private String embeddingApiKey;
    /** Embedding 模型名 */
    private String embeddingModel = "text-embedding-3-small";
    /** Embedding 向量维度 */
    private Integer embeddingDimensions = 1536;
    private int maxHistory = 20;
    private Duration timeout = Duration.ofSeconds(60);

    /**
     * 上网搜索 key
     */
    private String tavilyApiKey;
}
