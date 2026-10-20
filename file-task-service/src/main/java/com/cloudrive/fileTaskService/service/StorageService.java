package com.cloudrive.fileTaskService.service;

/**
 * 存储清理能力（清理任务只做删除，不上传/下载）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public interface StorageService {

    /**
     * 删除单个 OSS 对象
     *
     * @param path 对象 key
     */
    void deleteFile(String path);

    /**
     * 中止未完成的分片上传（清理 OSS 侧残留分片）
     *
     * @param ossUploadId OSS 分片上传 ID
     * @param objectKey   对象 key
     */
    void cancelUpload(String ossUploadId, String objectKey);
}
