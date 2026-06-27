package com.ff.mycloud.model.entity;

import com.ff.mycloud.enums.UploadTaskStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上传任务实体类
 * <p>
 * 用于追踪单个文件上传任务的状态和进度。实现Serializable接口
 * 以支持在Redis中序列化存储。包含任务ID、文件名、已传输字节数、
 * 进度百分比、完成状态、成功/失败标识等核心字段。
 * 任务状态通过UploadTaskStatus枚举管理（PENDING/UPLOADING/SUCCESS/FAILED/CANCELLED）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/3 14:00
 * @version 1.0
 */
@Data
public class UploadTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务唯一标识 */
    private final String id;

    /** 文件名 */
    private final String filename;

    /** 文件总大小（字节） */
    private long totalSize;

    /** 已传输字节数 */
    private long bytesTransferred;

    /** 上传进度（0.0 ~ 1.0） */
    private double progress;

    /** 是否已完成（成功或失败） */
    private boolean completed;

    /** 是否上传成功 */
    private boolean success;

    /** 状态消息或错误信息 */
    private String message;

    /** 任务创建时间戳（毫秒） */
    private final long createdAt;

    /** 上传任务状态 */
    private UploadTaskStatus status;

    /** 是否已释放预分配空间（失败/取消时） */
    private boolean spaceReleased;

    /**
     * 创建上传任务
     * <p>初始化状态为PENDING，进度为0。</p>
     *
     * @param id        任务唯一ID
     * @param filename  文件名
     * @param totalSize 文件总大小（字节）
     */
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
