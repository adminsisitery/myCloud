package com.cloudrive.fileDownloadService.model.vo;

import com.cloudrive.pan_common.domain.folder.FolderDownloadStatus;

/**
 * 文件夹打包任务视图
 * <p>
 * 字段与单体 {@code FolderDownloadTaskVO} 保持一致，前端契约不变。
 * </p>
 *
 * @param taskId         任务ID
 * @param folderName     文件夹名
 * @param status         任务状态
 * @param totalBytes     源文件总字节数
 * @param processedBytes 已处理字节数
 * @param progress       打包进度
 * @param message        状态说明
 * @param downloadable   压缩包是否可下载
 * @param expiresAt      压缩包过期时间戳
 * @author 飞飞
 * @date 2026/10/19
 */
public record FolderDownloadTaskVO(
        String taskId,
        String folderName,
        FolderDownloadStatus status,
        long totalBytes,
        long processedBytes,
        double progress,
        String message,
        boolean downloadable,
        long expiresAt) {
}
