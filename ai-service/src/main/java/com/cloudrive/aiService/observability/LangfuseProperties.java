package com.cloudrive.aiService.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "langfuse")
public class LangfuseProperties {
    private boolean captureContent = false;
    /**
     * Langfuse 公钥 (pk-lf-...)
     */
    private String publicKey;
    /**
     * Langfuse 私钥 (sk-lf-...)
     */
    private String secretKey;
    /**
     * Langfuse 地址，默认 Cloud
     */
    private String baseUrl = "https://cloud.langfuse.com";
}
