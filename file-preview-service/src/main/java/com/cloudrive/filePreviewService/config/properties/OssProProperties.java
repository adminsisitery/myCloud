package com.cloudrive.filePreviewService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 对象存储配置属性类（预览服务最小集）
 * <p>
 * 与 file-upload-service / file-download-service / 原单体保持同一套 {@code oss} 前缀配置。
 * 预览服务只使用预签名 URL 能力，不需要 STS/直传配置。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {
    /** OSS 服务端点地址（如 https://oss-cn-hangzhou.aliyuncs.com） */
    private String endpoint;
    /** OSS 访问密钥 ID */
    private String accessKeyId;
    /** OSS 访问密钥 Secret */
    private String accessKeySecret;
    /** OSS 存储桶名称 */
    private String bucketName;
    /** CDN/自定义域名，配置后以 CNAME 模式作为签名 endpoint */
    private String cdnEndpoint;
    /** 是否启用 OSS 存储，默认关闭 */
    private Boolean enabled = false;
}
