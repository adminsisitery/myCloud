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
     * 是否持有「尚未结清的预占额度」
     * <p>reserveSpace 成功后置 true，两种情况下清除：① 归还成功（此时 spaceReleased 也置位）；
     * ② 文件已落库、预占转为真实占用（{@code clearSpaceReserved}）。</p>
     * <p>存在的意义：超时扫描不能只看 status。取消会把任务置为终态 CANCELLED，
     * 而释放只由持有上传权的异步线程负责 —— 线程从未执行时状态是终态、归还却从未发生。
     * 有了这个标记，扫描可以直接问「还有没有没结清的预占」，不再依赖状态机。</p>
     */
    private boolean spaceReserved;

    /**
     * 任务所属用户ID
     * <p>超时扫描任务在异步线程已消失的情况下，靠它代为归还预占空间。
     * 加字段前写入 Redis 的历史任务反序列化后为 null，此时跳过归还并告警。</p>
     */
    private Long userId;

    /**
     * 创建上传任务（不带用户ID）
     * <p>保留此重载仅为兼容历史调用；新代码应使用带 userId 的构造函数。</p>
     *
     * @param id        任务唯一ID
     * @param filename  文件名
     * @param totalSize 文件总大小（字节）
     */
    public UploadTask(String id, String filename, long totalSize) {
        this(id, filename, totalSize, null);
    }

    /**
     * 创建上传任务
     * <p>初始化状态为PENDING，进度为0。</p>
     *
     * @param id        任务唯一ID
     * @param filename  文件名
     * @param totalSize 文件总大小（字节）
     * @param userId    任务所属用户ID
     */
    public UploadTask(String id, String filename, long totalSize, Long userId) {
        this.id = id;
        this.filename = filename;
        this.totalSize = totalSize;
        this.userId = userId;
        this.bytesTransferred = 0;
        this.progress = 0;
        this.completed = false;
        this.success = false;
        this.message = "";
        this.createdAt = System.currentTimeMillis();
        this.status = UploadTaskStatus.PENDING;
        this.spaceReleased = false;
        this.spaceReserved = false;
    }

    /**
     * 是否持有「尚未结清的预占额度」
     * <p>归还幂等闸与超时扫描都靠它决策，判定<b>不依赖状态机</b>：</p>
     * <ul>
     *   <li>{@link #spaceReserved}：reserveSpace 成功后显式置位，能覆盖「用户取消后
     *       异步线程从未执行」这类已是终态、但归还从未发生的任务 —— 这正是只看 status
     *       会漏掉的那一类；</li>
     *   <li>兜底：加该字段之前写入 Redis 的历史任务（反序列化后 spaceReserved 为 false），
     *       只要仍是 PENDING / UPLOADING 就按「可能持有预占」处理，不改变原有行为。</li>
     * </ul>
     * <p>已归还（{@link #spaceReleased}）或已落库成功（SUCCESS）一律为 false：前者归还过、
     * 后者预占已转真实占用，都不该再被扫描处理 —— 重复归还只会刷一堆无意义告警，
     * 而对已落库的任务归还则是资损（文件在、额度却退了）。</p>
     *
     * @return true 表示这笔预占还没结清（既没归还，也没转为真实占用）
     */
    public boolean holdsUnsettledReservation() {
        if (spaceReleased) {
            return false;
        }
        // SUCCESS 意味着文件已落库（completeUploadTask(true) 只在持久化之后调用），
        // 这笔预占已经转为真实占用。即便标记因故没清掉，也绝不能退 —— 退了就是资损。
        if (status == UploadTaskStatus.SUCCESS) {
            return false;
        }
        if (spaceReserved) {
            return true;
        }
        return status == UploadTaskStatus.PENDING || status == UploadTaskStatus.UPLOADING;
    }
}
