package com.ff.mycloud.model.vo;

import lombok.Data;

/**
 * 上传进度视图对象
 * 功能描述：用于向前端实时推送文件上传的进度信息，包括任务标识、文件大小、已传输字节数、完成状态和处理结果等
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class UploadProgressVO {

    /** 上传任务的唯一标识ID */
    private String taskId;

    /** 正在上传的文件名 */
    private String filename;

    /** 文件总大小，单位为字节 */
    private long totalSize;

    /** 已传输的字节数 */
    private long bytesTransferred;

    /** 上传进度百分比，取值范围 0.0 ~ 100.0 */
    private double progress;

    /** 上传是否已完成 */
    private boolean completed;

    /** 上传是否成功 */
    private boolean success;

    /** 上传状态描述 */
    private String status;

    /** 上传结果消息，成功时可为空，失败时包含错误信息 */
    private String message;
}
