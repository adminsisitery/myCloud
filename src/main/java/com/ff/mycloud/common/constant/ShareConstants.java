package com.ff.mycloud.common.constant;

/**
 * 文件分享相关常量，包含Redis键前缀、Token过期时间等配置。
 *
 * @author 飞飞
 * @date 2026/6/27
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