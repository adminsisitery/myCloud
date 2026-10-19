package com.cloudrive.fileShareService.common;

/**
 * 文件分享相关常量：Redis 键前缀、Token 有效期、分享 Cookie 名前缀。
 * <p>
 * 键名与迁移前的单体实现保持一致，便于共用同一份 Redis 数据。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public final class ShareConstants {

    private ShareConstants() {
    }

    /**
     * Redis 键定义
     */
    public static final class Redis {

        private Redis() {
        }

        /** 分享访问 Token 键前缀：share_token:{shareCode} */
        public static final String TOKEN_PREFIX = "share_token:";

        /** 分享过期延迟队列 */
        public static final String SHARE_QUEUE = "share:expire:queue";
    }

    /**
     * Token 相关定义
     */
    public static final class Token {

        private Token() {
        }

        /** Token / 分享 Cookie 有效期：24 小时（秒） */
        public static final long EXPIRE_TIME = 24 * 60 * 60;

        /** 分享 Token 的 Cookie 名前缀：share_token_{shareCode} */
        public static final String SHARE_TOKEN_COOKIE_PREFIX = "share_token_";
    }
}
