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

} 