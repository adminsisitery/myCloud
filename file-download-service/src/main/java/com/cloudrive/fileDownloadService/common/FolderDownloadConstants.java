package com.cloudrive.fileDownloadService.common;

/**
 * 文件夹打包下载相关常量
 * <p>
 * 队列/交换机/Redis 键前缀必须与单体、file-async-service 中的定义保持一致：
 * 创建任务时写入共享 Redis，并投递到 folder.download.queue 由异步服务消费打包。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public final class FolderDownloadConstants {

    /** 打包任务交换机 */
    public static final String EXCHANGE = "folder.download.topic";

    /** 打包任务队列（由 file-async-service 消费） */
    public static final String QUEUE = "folder.download.queue";

    /** 打包任务路由键 */
    public static final String ROUTING_KEY = "folder.download.create";

    /** 任务在 Redis 中的键前缀 */
    public static final String REDIS_PREFIX = "folder:download:";

    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "dlx:exchange";

    /** 死信队列 */
    public static final String DLX_QUEUE = "dlx:queue";

    /** 死信路由键 */
    public static final String DLX_ROUTING_KEY = "dlx";

    private FolderDownloadConstants() {
    }
}
