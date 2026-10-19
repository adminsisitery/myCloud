package com.cloudrive.fileDownloadService.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.cloudrive.fileDownloadService.config.properties.OssProProperties;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云 OSS 文件存储服务实现（下载服务最小集）
 * <p>
 * 从 file-upload-service 的 {@code OssStorageServiceImpl} 中裁剪出下载所需的部分：
 * Range 读取对象流、OSS 客户端创建/关闭、启用开关校验。
 * 返回的流在关闭时会自动 shutdown 对应的 OSS 客户端，避免连接泄漏。
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
     * 按闭区间 [start, end] 读取 OSS 对象
     *
     * @param path  存储对象路径（t_file_info.path）
     * @param start 起始字节（含）
     * @param end   结束字节（含）
     * @return 对象区间输入流
     */
    @Override
    public InputStream getFileRange(String path, long start, long end) {
        checkOssEnabled();
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("无效的对象读取范围");
        }
        OSS ossClient = getOssClient();
        try {
            GetObjectRequest request = new GetObjectRequest(ossProProperties.getBucketName(), path);
            request.setRange(start, end);
            OSSObject object = ossClient.getObject(request);
            return new OssManagedInputStream(object.getObjectContent(), ossClient);
        } catch (RuntimeException e) {
            logger.error("OSS对象读取失败: objectKey={}", path, e);
            closeOssClient(ossClient);
            throw e;
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
     * 安全关闭 OSS 客户端连接，释放资源
     *
     * @param ossClient OSS 客户端实例，可为 null
     */
    private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            ossClient.shutdown();
        }
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

    /**
     * 关闭流时同步关闭 OSS 客户端，避免连接池泄漏
     */
    private static final class OssManagedInputStream extends FilterInputStream {
        private final OSS ossClient;
        private boolean closed;

        private OssManagedInputStream(InputStream inputStream, OSS ossClient) {
            super(inputStream);
            this.ossClient = ossClient;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                ossClient.shutdown();
            }
        }
    }
}
