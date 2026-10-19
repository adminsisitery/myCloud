package com.cloudrive.fileManageService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.cloudrive.fileManageService.config.properties.OssProProperties;
import com.cloudrive.fileManageService.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 阿里云 OSS 对象存储服务实现（删除最小集）
 * <p>
 * 从 file-upload-service 的 {@code OssStorageServiceImpl} 中裁剪出彻底删除回收站
 * 文件所需的部分：启用开关校验 + deleteObject + 客户端关闭。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class OssStorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final OssProProperties ossProProperties;

    public OssStorageServiceImpl(OssProProperties ossProProperties) {
        this.ossProProperties = ossProProperties;
    }

    /**
     * 删除对象存储中的物理文件
     *
     * @param path 对象存储路径（t_file_info.path）
     */
    @Override
    public void deleteFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        if (!Boolean.TRUE.equals(ossProProperties.getEnabled())) {
            logger.warn("OSS 未启用，跳过物理文件删除: {}", path);
            return;
        }
        OSS ossClient = getOssClient();
        try {
            ossClient.deleteObject(ossProProperties.getBucketName(), path);
            logger.info("OSS 物理文件删除成功: {}", path);
        } catch (RuntimeException e) {
            logger.error("OSS 物理文件删除失败: {}", path, e);
            throw e;
        } finally {
            closeOssClient(ossClient);
        }
    }

    /**
     * 创建 OSS 客户端实例
     *
     * @return OSS 客户端
     */
    private OSS getOssClient() {
        return new OSSClientBuilder().build(
                ossProProperties.getEndpoint(),
                ossProProperties.getAccessKeyId(),
                ossProProperties.getAccessKeySecret());
    }

    /**
     * 安全关闭 OSS 客户端
     *
     * @param ossClient OSS 客户端实例，可为 null
     */
    private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
