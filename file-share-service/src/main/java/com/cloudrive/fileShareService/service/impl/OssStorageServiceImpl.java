package com.cloudrive.fileShareService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.cloudrive.fileShareService.config.properties.OssProProperties;
import com.cloudrive.fileShareService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云 OSS 对象存储服务实现
 * <p>
 * 由 file-upload-service 的 OSS 实现裁剪而来：彻底删除回收站文件（deleteObject）、
 * 分享/文件夹打包下载读取对象流（getObject + 流关闭时 shutdown 客户端）、
 * 单文件分享下载读取完整字节。
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
     * 读取对象存储中的文件流（调用方负责关闭，关闭时会 shutdown OSS 客户端）
     *
     * @param path 对象存储路径
     * @return 文件输入流
     */
    @Override
    public InputStream getFile(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(ossProProperties.getEnabled())) {
            throw new BusinessException(ErrorCode.OSS_DISABLED);
        }
        OSS ossClient = getOssClient();
        try {
            OSSObject ossObject = ossClient.getObject(ossProProperties.getBucketName(), path);
            return new OssObjectInputStream(ossObject, ossClient);
        } catch (RuntimeException e) {
            closeOssClient(ossClient);
            logger.error("OSS 读取文件失败: {}", path, e);
            throw new BusinessException(ErrorCode.OSS_DOWNLOAD_FAILED, "从OSS下载文件失败");
        }
    }

    /**
     * 读取对象存储中的完整文件字节
     *
     * @param path 对象存储路径
     * @return 文件字节数组
     */
    @Override
    public byte[] downloadFile(String path) {
        try (InputStream input = getFile(path)) {
            return input.readAllBytes();
        } catch (IOException e) {
            logger.error("读取 OSS 文件流失败: {}", path, e);
            throw new BusinessException(ErrorCode.OSS_DOWNLOAD_FAILED, "读取OSS文件流失败");
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

    /**
     * OSS 对象输入流：关闭流时同时关闭 OSSObject 并释放客户端
     */
    private static final class OssObjectInputStream extends FilterInputStream {

        private final OSSObject ossObject;
        private final OSS ossClient;

        private OssObjectInputStream(OSSObject ossObject, OSS ossClient) {
            super(ossObject.getObjectContent());
            this.ossObject = ossObject;
            this.ossClient = ossClient;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    ossObject.close();
                } finally {
                    ossClient.shutdown();
                }
            }
        }
    }
}
