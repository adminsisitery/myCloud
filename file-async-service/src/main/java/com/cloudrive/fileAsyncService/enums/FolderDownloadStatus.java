package com.cloudrive.fileAsyncService.enums;

/**
 * 文件夹打包任务状态（与单体一致，Redis 序列化兼容）
 */
public enum FolderDownloadStatus {
    PENDING,
    PACKAGING,
    UPLOADING,
    SUCCESS,
    FAILED,
    CANCELLED
}
