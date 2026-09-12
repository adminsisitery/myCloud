package com.ff.mycloud.model.vo;

import com.ff.mycloud.enums.FolderDownloadStatus;

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
