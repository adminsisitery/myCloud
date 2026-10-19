package com.cloudrive.pan_common.domain.folder;

/**
 * 文件夹异步打包任务状态
 * <p>
 * 任务对象存放在共享 Redis 中，由 file-download-service 写入、
 * file-async-service 消费打包，因此枚举/实体必须来自公共库，保证两端
 * 类名（Redisson 序列化会记录类名）与字段结构完全一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public enum FolderDownloadStatus {
    PENDING,
    PACKAGING,
    UPLOADING,
    SUCCESS,
    FAILED,
    CANCELLED
}
