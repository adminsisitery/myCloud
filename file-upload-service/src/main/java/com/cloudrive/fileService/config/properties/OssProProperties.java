package com.cloudrive.fileService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OSS 对象存储配置属性类
 * <p>
 * 读取配置文件中的 oss 前缀属性，包括 OSS 端点地址、访问密钥、存储桶名称及是否启用。
 * 通过 Spring Boot 的 @ConfigurationProperties 自动绑定属性值。
 * </p>
 *
 * @author 飞飞
 * @date 2026/2/22 19:36
 * @version 1.0
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
    /** CDN/自定义域名（仅用于生成预览签名URL），如 pre.feifei.ltd */
    private String cdnEndpoint;
    /** 是否启用 OSS 存储，默认关闭 */
    private Boolean enabled = false;

    /** STS 临时凭证配置。roleArn 为空时直传接口不可用。 */
    private Sts sts = new Sts();

    @Data
    public static class Sts {
        private String regionId = "cn-hangzhou";
        private String roleArn;
        private String roleSessionName = "mycloud-upload";
        private long durationSeconds = 900;
        /** 凭证过期后额外保留的确认窗口。 */
        private long confirmGraceSeconds = 900;
        private int maxParallelParts = 6;
    }
}
