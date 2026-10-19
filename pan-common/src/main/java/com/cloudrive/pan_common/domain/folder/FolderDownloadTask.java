package com.cloudrive.pan_common.domain.folder;

import lombok.Data;

/**
 * 文件夹异步打包任务（跨服务共享的 Redis 数据结构）
 * <p>
 * 由 file-download-service 创建并写入 Redis（key = folder:download:{taskId}，TTL 2 天），
 * file-async-service 消费 MQ 后读取/更新同一份任务对象并执行打包。
 * 两端必须使用同一个类（本类在 pan-common 中），否则 Redisson 默认编解码器
 * 反序列化时会因类名不一致失败。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class FolderDownloadTask {

    /** 任务ID（UUID 去横线） */
    private String taskId;

    /** 发起用户ID */
    private Long userId;

    /** 被打包的文件夹ID */
    private Long folderId;

    /** 文件夹名称（用于下载文件名） */
    private String folderName;

    /** 任务状态 */
    private FolderDownloadStatus status;

    /** 源文件总字节数 */
    private long totalBytes;

    /** 已处理字节数 */
    private long processedBytes;

    /** 打包进度（0.0 - 1.0） */
    private double progress;

    /** 打包完成后的临时压缩包对象键（OSS） */
    private String objectKey;

    /** 状态说明 */
    private String message;

    /** 是否已请求取消 */
    private boolean cancelRequested;

    /** 创建时间戳（毫秒） */
    private long createdAt;

    /** 最后更新时间戳（毫秒） */
    private long updatedAt;

    /** 压缩包过期时间戳（毫秒），SUCCESS 后 24 小时 */
    private long expiresAt;
}
