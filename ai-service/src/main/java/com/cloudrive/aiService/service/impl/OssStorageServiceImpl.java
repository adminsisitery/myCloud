package com.cloudrive.aiService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.cloudrive.aiService.config.properties.OssProProperties;
import com.cloudrive.aiService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 存储服务实现
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class OssStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final OssProProperties ossProProperties;

    public OssStorageServiceImpl(OssProProperties ossProProperties) {
        this.ossProProperties = ossProProperties;
    }

    @Override
    public String UploadFile(MultipartFile file, String path) {
        checkOssEnabled();
        String objectName = buildObjectName(path, generateUniqueFileName(file.getOriginalFilename()));
        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            ObjectMetadata metadata = new ObjectMetadata();
            String contentType = file.getContentType();
            metadata.setContentType(contentType == null || contentType.isEmpty()
                    ? "application/octet-stream" : contentType);
            ossClient.putObject(new PutObjectRequest(
                    ossProProperties.getBucketName(), objectName, file.getInputStream(), metadata));
            return objectName;
        } catch (Exception e) {
            log.error("OSS 上传失败: bucket={}, objectKey={}", ossProProperties.getBucketName(), objectName, e);
            ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, e.getMessage());
            return null;
        } finally {
            closeOssClient(ossClient);
        }
    }

    @Override
    public InputStream getFile(String path) {
        checkOssEnabled();
        final OSS ossClient = getOssClient();
        try {
            OSSObject object = ossClient.getObject(ossProProperties.getBucketName(), path);
            return new FilterInputStream(object.getObjectContent()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        try {
                            object.close();
                        } catch (Exception ignored) {
                            // 关闭对象失败不影响调用方
                        }
                        closeOssClient(ossClient);
                    }
                }
            };
        } catch (Exception e) {
            closeOssClient(ossClient);
            log.error("OSS 读取失败: bucket={}, objectKey={}", ossProProperties.getBucketName(), path, e);
            ExceptionUtil.throwBizException(ErrorCode.OSS_DOWNLOAD_FAILED, e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteFile(String path) {
        if (!Boolean.TRUE.equals(ossProProperties.getEnabled())) {
            log.warn("OSS 未启用，跳过删除: {}", path);
            return;
        }
        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            ossClient.deleteObject(ossProProperties.getBucketName(), path);
        } catch (Exception e) {
            log.warn("删除 OSS 对象失败（忽略）: {}", path, e);
        } finally {
            closeOssClient(ossClient);
        }
    }

    private void checkOssEnabled() {
        if (!Boolean.TRUE.equals(ossProProperties.getEnabled())) {
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }

    private OSS getOssClient() {
        return new OSSClientBuilder().build(
                ossProProperties.getEndpoint(),
                ossProProperties.getAccessKeyId(),
                ossProProperties.getAccessKeySecret());
    }

    private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            try {
                ossClient.shutdown();
            } catch (Exception e) {
                log.warn("关闭 OSS 客户端失败: {}", e.getMessage());
            }
        }
    }

    private String generateUniqueFileName(String originalFilename) {
        String suffix = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0) {
                suffix = originalFilename.substring(dot);
            }
        }
        return UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private String buildObjectName(String path, String fileName) {
        if (path == null || path.isEmpty()) {
            return fileName;
        }
        return path.endsWith("/") ? path + fileName : path + "/" + fileName;
    }
}
