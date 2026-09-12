package com.ff.mycloud.common.constant;

/**
 * 通用常量类
 */
public class CommonConstants {
    
    /**
     * HTTP状态码
     */
    public static class StatusCode {
        public static final int SUCCESS = 200;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int SERVER_ERROR = 500;
    }
    
    /**
     * 文件相关常量
     */
    public static class File {
        public static final String SLASH = "/";
        public static final String FILE_PATH_PREFIX = "user_";
        /**
         * 大文件标准 500mb
         */
        public static final long BIGFILE = 500000;
    }
    
    /**
     * 时间相关常量（毫秒）
     */
    public static class Time {
        public static final long ONE_MINUTE = 60 * 1000;
        public static final long THREE_MINUTES = 3 * ONE_MINUTE;
        public static final long FIVE_MINUTES = 5 * ONE_MINUTE;
        public static final long ONE_HOUR = 60 * ONE_MINUTE;
        public static final long ONE_DAY = 24 * ONE_HOUR;
        public static final long ONE_WEEK = 7 * ONE_DAY;
    }
    
    /**
     * 限流相关常量
     */
    public static class RateLimit {
        public static final String KEY_PREFIX = "ratelimit:";
        public static final long DEFAULT_RATE = 3;
        public static final String ANONYMOUS_USER = "anonymous";
        public static final String UNKNOWN_IP = "unknown";
    }

    public static class UserRole {
        public static final long ADMIN_ID = 1;
        public static final long USER_ID = 2;
    }

    /**
     * 登录防爆破常量
     */
    public static class LoginFail {
        /** Redis key 前缀：账户维度 */
        public static final String ACCOUNT_KEY_PREFIX = "login:fail:account:";
        /** Redis key 前缀：IP 维度 */
        public static final String IP_KEY_PREFIX = "login:fail:ip:";
        /** 账户维度最大失败次数（达到后硬锁） */
        public static final int ACCOUNT_MAX_FAIL = 5;
        /** IP 维度最大失败次数（1分钟内） */
        public static final int IP_MAX_FAIL = 20;
        /** 账户硬锁时长（秒） */
        public static final long ACCOUNT_LOCK_SECONDS = 15 * 60;
        /** IP 维度计数窗口（秒） */
        public static final long IP_WINDOW_SECONDS = 60;
    }

    /**
     * MQ 消息去重常量
     */
    public static class Dedup {
        /** Redis key 前缀 */
        public static final String KEY_PREFIX = "msg:consumed:";
        /** 去重记录 TTL（秒） */
        public static final long TTL_SECONDS = 24 * 60 * 60;
    }

    /**
     * 操作日志 MQ 常量
     */
    public static class LogMQ {
        public static final String EXCHANGE = "log.topic";
        public static final String QUEUE = "log.queue";
        public static final String KEY = "operation_log";
    }

    public static class FolderDownloadMQ {
        public static final String EXCHANGE = "folder.download.topic";
        public static final String QUEUE = "folder.download.queue";
        public static final String KEY = "folder.download.create";
        public static final String REDIS_PREFIX = "folder:download:";
    }


    /**
     * 文件分片上传
     */
    public static class ChunkUpload {
        public static final String KEY_PREFIX = "chunk:upload:";
        public static final String KEY_PARTS = ":parts";
    }

    /**
     * 最近访问文件
     */
    public static class RecentFile {
        public static final int N = 10;
    }

    public static class PG {
        public static final String TOPIC_EXCHANGE = "pg.topic";
        public static final String INSERT_KEY = "PG_insert";
        public static final String INSERT_QUEUE = "pg.insert.queue";
        public static final String DELETE_KEY = "PG_delete";
        public static final String DELETE_QUEUE = "pg.delete.queue";
    }

    public static class USAGE {
        public static final String KEY_PREFIX = "usage:";
        public static final String USAGE_EXCHANGE = "usage:exchange";
        public static final String USAGE_QUEUE = "usage:queue";
        public static final String USAGE_KEY = "usage";
        public static final String USAGE_TOPIC = "usage:topic";
    }

    /** 死信队列常量 */
    public static class DLQ {
        public static final String DLX_EXCHANGE = "dlx:exchange";
        public static final String DLX_QUEUE = "dlx:queue";
        public static final String DLX_KEY = "dlx";
    }
}
