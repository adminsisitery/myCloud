package com.cloudrive.fileAsyncService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 嵌入模型配置（只取异步服务需要的子集）
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class EmbeddingProperties {

    /** 对话模型 Key（embeddingApiKey 为空时复用它） */
    private String apiKey;

    /** 对话模型地址（embeddingBaseUrl 为空时复用它） */
    private String baseUrl = "https://api.deepseek.com";

    private String embeddingBaseUrl;

    private String embeddingApiKey;

    private String embeddingModel = "text-embedding-v4";

    private Integer embeddingDimensions = 1536;

    private Duration timeout = Duration.ofSeconds(60);
}
