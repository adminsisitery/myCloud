package com.ff.mycloud.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/22 19:36
 * @version 1.0
 */

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private Boolean enabled = false;
}
