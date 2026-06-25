package com.ff.mycloud.model.entity;

import com.ff.mycloud.enums.UploadTaskStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/3 14:00
 * @version 1.0
 */

@Data
public class UploadTask implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String filename;
    private long totalSize;
    private long bytesTransferred;
    private double progress;
    private boolean completed;
    private boolean success;
    private String message;
    private final long createdAt;
    private UploadTaskStatus status;
    private boolean spaceReleased;

    public UploadTask(String id, String filename, long totalSize) {
        this.id = id;
        this.filename = filename;
        this.totalSize = totalSize;
        this.bytesTransferred = 0;
        this.progress = 0;
        this.completed = false;
        this.success = false;
        this.message = "";
        this.createdAt = System.currentTimeMillis();
        this.status = UploadTaskStatus.PENDING;
        this.spaceReleased = false;
    }
}
