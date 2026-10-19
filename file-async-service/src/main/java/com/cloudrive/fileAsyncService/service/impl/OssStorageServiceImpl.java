package com.cloudrive.fileAsyncService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.cloudrive.fileAsyncService.config.properties.OssProProperties;
import com.cloudrive.fileAsyncService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.ExceptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * OSS 只读实现
 * <p>
 * 异步服务不需要上传能力；流关闭时同时关闭 OSSObject 与 OSSClient，避免连接泄漏。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssStorageServiceImpl implements StorageService {

    private final OssProProperties properties;

    @Override
    public InputStream getFile(String path) {
        checkOssEnabled();
        OSS ossClient = null;
        try {
            ossClient = buildClient();
            OSSObject object = ossClient.getObject(properties.getBucketName(), path);
            if (object == null || object.getObjectContent() == null) {
                shutdown(ossClient);
                ExceptionUtil.throwBizException(ErrorCode.OSS_DOWNLOAD_FAILED, "对象不存在: " + path);
            }
            final OSS clientRef = ossClient;
            return new FilterInputStream(object.getObjectContent()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        try {
                            object.close();
                        } catch (Exception ignored) {
                            // OSSObject 已关闭
                        }
                        shutdown(clientRef);
                    }
                }
            };
        } catch (RuntimeException e) {
            shutdown(ossClient);
            throw e;
        } catch (Exception e) {
            shutdown(ossClient);
            log.error("OSS 读取失败: path={}", path, e);
            ExceptionUtil.throwBizException(ErrorCode.OSS_DOWNLOAD_FAILED, "OSS 读取失败: " + path);
            return null;
        }
    }

    @Override
    public void uploadObject(String objectKey, InputStream input, long size, String contentType) {
        checkOssEnabled();
        OSS ossClient = null;
        try {
            ossClient = buildClient();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            ossClient.putObject(new PutObjectRequest(properties.getBucketName(), objectKey, input, metadata));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSS 上传失败: objectKey={}", objectKey, e);
            ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, "OSS 上传失败: " + objectKey);
        } finally {
            shutdown(ossClient);
        }
    }

    @Override
    public void deleteFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        checkOssEnabled();
        OSS ossClient = null;
        try {
            ossClient = buildClient();
            ossClient.deleteObject(properties.getBucketName(), path);
        } catch (Exception e) {
            // 临时压缩包清理失败不阻断主流程
            log.warn("OSS 删除失败: path={}", path, e);
        } finally {
            shutdown(ossClient);
        }
    }

    private OSS buildClient() {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    private void checkOssEnabled() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }

    private void shutdown(OSS ossClient) {
        if (ossClient == null) {
            return;
        }
        try {
            ossClient.shutdown();
        } catch (Exception e) {
            log.warn("关闭 OSS 客户端失败", e);
        }
    }
}
