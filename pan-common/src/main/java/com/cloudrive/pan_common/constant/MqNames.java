package com.cloudrive.pan_common.constant;

/**
 * 跨服务 MQ 名称常量（交换机 / 队列 / 路由键）
 * <p>
 * 生产端（file-upload-service、file-manage-service 等）与消费端（file-async-service）
 * 必须使用完全一致的名称与参数，否则消息无法投递或队列重复声明会触发 PRECONDITION_FAILED。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public final class MqNames {

    private MqNames() {
    }

    /** ES 索引同步（Topic 交换机） */
    public static final class Es {
        public static final String EXCHANGE = "ES.topic";
        public static final String INSERT_QUEUE = "ES.insert.queue";
        public static final String DELETE_QUEUE = "ES.delete.queue";
        public static final String UPDATE_QUEUE = "ES.update.queue";
        public static final String INSERT_KEY = "ES_insert";
        public static final String DELETE_KEY = "ES_delete";
        public static final String UPDATE_KEY = "ES_update";

        private Es() {
        }
    }

    /** PG 向量同步（Direct 交换机） */
    public static final class Pg {
        public static final String EXCHANGE = "pg.topic";
        public static final String INSERT_QUEUE = "pg.insert.queue";
        public static final String DELETE_QUEUE = "pg.delete.queue";
        public static final String INSERT_KEY = "PG_insert";
        public static final String DELETE_KEY = "PG_delete";

        private Pg() {
        }
    }

    /** 操作日志（Direct 交换机） */
    public static final class Log {
        public static final String EXCHANGE = "log.topic";
        public static final String QUEUE = "log.queue";
        public static final String KEY = "operation_log";

        private Log() {
        }
    }

    /** 用量通知（Topic 交换机） */
    public static final class Usage {
        public static final String EXCHANGE = "usage:exchange";
        public static final String QUEUE = "usage:queue";
        public static final String KEY = "usage";

        private Usage() {
        }
    }

    /** 死信路由 */
    public static final class Dlq {
        public static final String EXCHANGE = "dlx:exchange";
        public static final String QUEUE = "dlx:queue";
        public static final String KEY = "dlx";

        private Dlq() {
        }
    }
    /** 文件夹打包任务（Topic 交换机，任务状态存 Redis、压缩包存 OSS） */
    public static final class FolderDownload {
        public static final String EXCHANGE = "folder.download.topic";
        public static final String QUEUE = "folder.download.queue";
        public static final String KEY = "folder.download.create";

        private FolderDownload() {
        }
    }
}
