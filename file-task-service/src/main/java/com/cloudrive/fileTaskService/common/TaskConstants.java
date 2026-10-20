package com.cloudrive.fileTaskService.common;

/**
 * 定时任务服务常量
 * <p>
 * 锁 key / Redis 前缀 / 保留时长等。锁 key 与原任务所在服务保持一致，
 * 便于滚动发布期间新旧实例互斥，也避免迁移后出现两个扫描同时执行。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public final class TaskConstants {

    private TaskConstants() {
    }

    /** 文件夹打包任务 Redis key 前缀（与 file-download-service / file-async-service 共享） */
    public static final String FOLDER_DOWNLOAD_REDIS_PREFIX = "folder:download:";

    /** 文件夹打包临时压缩包保留时长：24 小时 */
    public static final long FOLDER_ARCHIVE_TTL_MS = 24 * 60 * 60 * 1000L;

    /** 回收站保留天数 */
    public static final long TRASH_RETENTION_DAYS = 30L;

    /** 普通上传超时扫描锁 */
    public static final String LOCK_UPLOAD_TIMEOUT_SCAN = "lock:file_upload_timeout_scan";

    /** 分片上传超时扫描锁 */
    public static final String LOCK_CHUNK_TIMEOUT_SCAN = "lock:chunk_upload_timeout_scan";

    /** 直传超时扫描锁 */
    public static final String LOCK_DIRECT_UPLOAD_TIMEOUT_SCAN = "lock:direct_upload_timeout_scan";

    /** 分享过期标记锁 */
    public static final String LOCK_SHARE_EXPIRE_MARK = "lock:share_expire_mark";

    /** 文件夹打包清理锁 */
    public static final String LOCK_FOLDER_DOWNLOAD_CLEANUP = "lock:folder_download_cleanup";

    /** 回收站清理锁 */
    public static final String LOCK_TRASH_CLEANUP = "lock:trash_cleanup";
}
