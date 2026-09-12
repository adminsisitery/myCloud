package com.ff.mycloud.model.entity;

import com.ff.mycloud.enums.FolderDownloadStatus;
import lombok.Data;

@Data
public class FolderDownloadTask {
    private String taskId;
    private Long userId;
    private Long folderId;
    private String folderName;
    private FolderDownloadStatus status;
    private long totalBytes;
    private long processedBytes;
    private double progress;
    private String objectKey;
    private String message;
    private boolean cancelRequested;
    private long createdAt;
    private long updatedAt;
    private long expiresAt;
}
