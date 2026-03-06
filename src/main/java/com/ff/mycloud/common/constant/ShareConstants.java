package com.ff.mycloud.common.constant;

/**
 * 文件分享相关常量
 */
public class ShareConstants {
    
    /**
     * Redis key 前缀
     */
    public static class Redis {
        public static final String TOKEN_PREFIX = "share_token:";
        public static final String SHARE_QUEUE = "share:expire:queue";
    }
    
    /**
     * Token 相关常量
     */
    public static class Token {
        public static final long EXPIRE_TIME = 24 * 60 * 60; // 24小时
        public static final String SHARE_TOKEN_COOKIE_PREFIX = "share_token_";
    }
} 