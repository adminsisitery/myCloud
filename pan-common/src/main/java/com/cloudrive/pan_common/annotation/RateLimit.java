package com.cloudrive.pan_common.annotation;



import com.cloudrive.pan_common.exceotion.RateLimitExceededException;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解，用于标记需要进行请求速率限制的方法。
 * 支持按用户、IP和全局三个维度进行限流，基于Redisson的RRateLimiter实现。
 * 当请求被限流时，可以指定降级方法或抛出指定的异常类。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 限流维度枚举
     */
    enum Dimension {
        /** 用户维度限流（按登录用户ID） */
        USER,
        /** IP维度限流（按客户端IP地址） */
        IP,
        /** 全局维度限流（所有请求共享） */
        GLOBAL
    }

    /**
     * 限流维度，支持同时指定多个维度进行组合限流
     *
     * @return 限流维度数组，默认为全局维度
     */
    Dimension[] dimensions() default {Dimension.GLOBAL};

    /**
     * 每秒允许的请求数（令牌产生速率）
     *
     * @return 每秒令牌数
     */
    double permitsPerSecond();

    /**
     * 获取令牌的超时时间
     *
     * @return 超时时间数值，默认为0表示不等待
     */
    long timeout() default 0;

    /**
     * 超时时间的单位
     *
     * @return 时间单位，默认为毫秒
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 降级方法名，当请求被限流时调用的替代方法
     *
     * @return 降级方法名称，默认为空字符串表示不降级
     */
    String fallback() default "";

    /**
     * 限流时抛出的异常类
     *
     * @return 异常类型，默认为RateLimitExceededException
     */
    Class<? extends Exception> exception() default RateLimitExceededException.class;
}
