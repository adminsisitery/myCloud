package com.cloudrive.fileAsyncService.service;

/**
 * 文件夹打包任务处理（异步服务只负责消费与打包，任务的创建/查询/取消仍在单体）
 */
public interface FolderDownloadTaskService {

    /**
     * 消费打包任务：打包 ZIP 并上传 OSS
     */
    void process(String taskId);

    /**
     * 清理过期压缩包与过期任务记录
     */
    void cleanupExpired();
}
