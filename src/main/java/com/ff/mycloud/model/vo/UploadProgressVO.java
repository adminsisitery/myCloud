package com.ff.mycloud.model.vo;

import lombok.Data;

/**
 * 上传进度VO
 */
@Data
public class UploadProgressVO {
    private String taskId;
    private String filename;
    private long totalSize;
    private long bytesTransferred;
    private double progress;
    private boolean completed;
    private boolean success;
    private String message;
}
