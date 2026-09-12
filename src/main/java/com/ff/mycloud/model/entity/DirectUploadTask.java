package com.ff.mycloud.model.entity;

import lombok.Data;

@Data
public class DirectUploadTask {
    private String taskId;
    private Long userId;
    private String objectKey;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Long parentId;
    private String sha256Hash;
    private Long createdAt;
    private Long expiresAt;
}
