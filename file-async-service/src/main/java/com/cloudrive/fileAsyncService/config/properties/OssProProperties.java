package com.cloudrive.fileAsyncService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 只读配置（PG 索引重建时需要读取原始对象提取文本）
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {

    private Boolean enabled = false;

    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private String cdnEndpoint;
}
