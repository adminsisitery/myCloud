package com.cloudrive.fileTaskService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.cloudrive.fileTaskService.config.properties.OssProProperties;
import com.cloudrive.fileTaskService.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OSS 清理实现
 * <p>
 * 清理任务的原则是「尽力而为」：物理对象删除失败只记日志，不阻断逻辑清理，
 * 否则残留任务会永远卡在扫描里反复重试。真正的空间回收（配额）由调用方负责。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Slf4j
@Service
public class OssStorageServiceImpl implements StorageService {

    private final OssProProperties oss;

    public OssStorageServiceImpl(OssProProperties oss) {
        this.oss = oss;
    }

    @Override
    public void deleteFile(String path) {
        if (path == null || path.isBlank() || !ossAvailable()) {
            return;
        }
        OSS client = buildClient();
        try {
            client.deleteObject(oss.getBucketName(), path);
            log.info("已删除 OSS 对象: {}", path);
        } catch (RuntimeException e) {
            log.warn("删除 OSS 对象失败（继续逻辑清理）: path={}", path, e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void cancelUpload(String ossUploadId, String objectKey) {
        if (ossUploadId == null || ossUploadId.isBlank() || objectKey == null || objectKey.isBlank() || !ossAvailable()) {
            return;
        }
        OSS client = buildClient();
        try {
            client.abortMultipartUpload(new AbortMultipartUploadRequest(oss.getBucketName(), objectKey, ossUploadId));
            log.info("已中止 OSS 分片上传: objectKey={}, uploadId={}", objectKey, ossUploadId);
        } catch (RuntimeException e) {
            log.warn("中止 OSS 分片上传失败（继续逻辑清理）: objectKey={}, uploadId={}", objectKey, ossUploadId, e);
        } finally {
            client.shutdown();
        }
    }

    /**
     * OSS 配置是否可用
     */
    private boolean ossAvailable() {
        if (!Boolean.TRUE.equals(oss.getEnabled())) {
            log.debug("OSS 未启用，跳过物理对象清理");
            return false;
        }
        if (isBlank(oss.getEndpoint()) || isBlank(oss.getAccessKeyId())
                || isBlank(oss.getAccessKeySecret()) || isBlank(oss.getBucketName())) {
            log.warn("OSS 配置不完整（endpoint/ak/sk/bucket 缺失），跳过物理对象清理");
            return false;
        }
        return true;
    }

    private OSS buildClient() {
        return new OSSClientBuilder().build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
