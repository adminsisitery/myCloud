package com.cloudrive.fileTaskService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 配置（最小集：清理任务只需要删除对象/中止分片上传）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {

    /** 是否启用 OSS */
    private Boolean enabled = false;

    /** OSS Endpoint */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;
}
