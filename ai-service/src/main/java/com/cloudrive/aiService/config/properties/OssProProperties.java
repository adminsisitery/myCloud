package com.cloudrive.aiService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性
 * <p>ai-service 仅在 SAVE_DOCUMENT 动作中写入 OSS、在 Agent 全文读取时读取 OSS。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {

    /** OSS 服务端点 */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** CDN 加速域名（可选） */
    private String cdnEndpoint;

    /** 是否启用 OSS（本地无 OSS 时可关闭） */
    private Boolean enabled = Boolean.FALSE;
}
