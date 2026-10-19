package com.cloudrive.fileShareService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 对象存储配置属性类（文件管理服务最小集）
 * <p>
 * 与原单体/file-upload-service 保持同一套 {@code oss} 前缀配置；
 * 本服务仅在「彻底删除回收站文件」时删除 OSS 对象，因此只需要
 * 端点、访问密钥、存储桶与启用开关，不需要 STS/直传配置。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {

    /** OSS 服务端点地址 */
    private String endpoint;

    /** OSS 访问密钥 ID */
    private String accessKeyId;

    /** OSS 访问密钥 Secret */
    private String accessKeySecret;

    /** OSS 存储桶名称 */
    private String bucketName;

    /** CDN/自定义域名（保留配置对齐，本服务暂未使用） */
    private String cdnEndpoint;

    /** 是否启用 OSS 存储，默认关闭 */
    private Boolean enabled = false;
}
