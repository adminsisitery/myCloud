package com.cloudrive.pan_common.exceotion;

import com.cloudrive.pan_common.annotation.RateLimit;

/**
 * 限流异常，当请求超过速率限制时抛出。
 * 通常由限流注解{@link RateLimit}触发。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * 构造限流异常
     *
     * @param message 异常消息
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
} 