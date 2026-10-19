package com.cloudrive.filePreviewService.service.impl;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.cloudrive.filePreviewService.config.properties.OssProProperties;
import com.cloudrive.filePreviewService.service.OssStorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 阿里云 OSS 预签名 URL 服务实现（预览服务最小集）
 * <p>
 * 从原单体 {@code OssStorageServiceImpl#generatePresignedUrl} 裁剪而来：
 * 配置了 CDN 域名时以 CDN 域名作为 endpoint 生成 CNAME 模式签名，否则使用默认 OSS endpoint；
 * 签名参数带 {@code response-content-disposition=inline}，让浏览器内联预览而不是下载。
 * 每次生成签名后关闭客户端，避免连接泄漏。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class OssStorageServiceImpl implements OssStorageService {

    private static final Logger logger = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final OssProProperties ossProProperties;

    public OssStorageServiceImpl(OssProProperties ossProProperties) {
        this.ossProProperties = ossProProperties;
    }

    /**
     * 生成预览用的 OSS 预签名 URL
     *
     * @param objectKey     OSS 对象 Key（t_file_info.path）
     * @param expirySeconds 签名有效期（秒）
     * @param mimeType      期望覆盖的 Content-Type（OSS 对 response-content-type 限制较多，
     *                      与原单体保持一致，仅设置 inline 响应头）
     * @return 签名 URL 字符串
     */
    @Override
    public String generatePresignedUrl(String objectKey, int expirySeconds, String mimeType) {
        checkOssEnabled();
        Date date = new Date(System.currentTimeMillis() + expirySeconds * 1000L);

        // 配置了 CDN 域名时，用 CDN 域名作为 endpoint 生成签名（CNAME 模式），签名天然有效
        OSS ossClient;
        if (ossProProperties.getCdnEndpoint() != null && !ossProProperties.getCdnEndpoint().isEmpty()) {
            ClientBuilderConfiguration config = new ClientBuilderConfiguration();
            config.setSupportCname(true);
            ossClient = new OSSClientBuilder().build(
                    ossProProperties.getCdnEndpoint(),
                    ossProProperties.getAccessKeyId(),
                    ossProProperties.getAccessKeySecret(),
                    config);
        } else {
            ossClient = getOssClient();
        }

        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(ossProProperties.getBucketName(), objectKey);
            request.setMethod(HttpMethod.GET);
            request.setExpiration(date);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("response-content-disposition", "inline");
            request.setQueryParameter(queryParams);

            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (RuntimeException e) {
            logger.error("OSS预签名URL生成失败: objectKey={}", objectKey, e);
            throw e;
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 创建 OSS 客户端实例
     *
     * @return 已配置的 OSS 客户端
     */
    private OSS getOssClient() {
        return new OSSClientBuilder().build(
                ossProProperties.getEndpoint(),
                ossProProperties.getAccessKeyId(),
                ossProProperties.getAccessKeySecret());
    }

    /**
     * 检查 OSS 存储服务是否已启用
     *
     * @throws com.cloudrive.pan_common.exceotion.BusinessException 当 OSS 未启用时抛出
     */
    private void checkOssEnabled() {
        if (!ossProProperties.getEnabled()) {
            logger.error("OSS storage is disabled");
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }
}
