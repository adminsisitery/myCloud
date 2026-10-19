package com.cloudrive.fileAsyncService.common;

/**
 * 异步服务使用的 MQ / Redis 常量
 * <p>
 * 与单体保持一致：队列名、交换机名、路由键、去重前缀、索引锁前缀均相同，
 * 这样单体生产端不需要改动即可被异步服务消费。
 * </p>
 */
public final class MqConstants {

    private MqConstants() {
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

    /** 死信路由 */
    public static final class Dlq {
        public static final String EXCHANGE = "dlx:exchange";
        public static final String QUEUE = "dlx:queue";
        public static final String KEY = "dlx";

        private Dlq() {
        }
    }

    /** MQ 消费幂等去重（Redis SETNX） */
    public static final class Dedup {
        public static final String KEY_PREFIX = "msg:consumed:";
        public static final long TTL_SECONDS = 24L * 60 * 60;

        private Dedup() {
        }
    }

    /** 用量通知（Topic 交换机） */
    public static final class Usage {
        public static final String EXCHANGE = "usage:exchange";
        public static final String QUEUE = "usage:queue";
        public static final String KEY = "usage";
        /** 同一天同一用户只通知一次的 Redis 去重前缀 */
        public static final String NOTIFIED_PREFIX = "usage:notified:";

        private Usage() {
        }
    }

    /** 文件夹打包任务（Topic 交换机，任务状态存 Redis、压缩包存 OSS） */
    public static final class FolderDownload {
        public static final String EXCHANGE = "folder.download.topic";
        public static final String QUEUE = "folder.download.queue";
        public static final String KEY = "folder.download.create";
        public static final String REDIS_PREFIX = "folder:download:";

        private FolderDownload() {
        }
    }

    /** ES / PG 索引按文件维度的 Redisson 锁前缀 */
    public static final String INDEX_LOCK_PREFIX = "file-index:";
}
