package com.cloudrive.filePreviewService.service;

/**
 * 对象存储服务（预览服务最小集）
 * <p>只保留生成 OSS 预签名 URL 的能力，用于前端在线预览。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface OssStorageService {

    /**
     * 生成预览用的 OSS 预签名 URL
     *
     * @param objectKey     OSS 对象 Key（t_file_info.path）
     * @param expirySeconds 签名有效期（秒），建议 300 秒
     * @param mimeType      期望覆盖的 Content-Type，null 表示不覆盖
     * @return 签名 URL 字符串
     */
    String generatePresignedUrl(String objectKey, int expirySeconds, String mimeType);
}
