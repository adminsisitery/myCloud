package com.cloudrive.pan_common.enums;

/**
 * 上传任务状态枚举
 * <p>
 * 定义文件上传任务的生命周期状态：
 * PENDING（等待中） -> UPLOADING（上传中） -> SUCCESS（成功）/ FAILED（失败）/ CANCELLED（已取消）。
 * 用于UploadTask实体的status字段。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public enum UploadTaskStatus {
    /** 等待上传 */
    PENDING,
    /** 正在上传 */
    UPLOADING,
    /** 上传成功 */
    SUCCESS,
    /** 上传失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}
