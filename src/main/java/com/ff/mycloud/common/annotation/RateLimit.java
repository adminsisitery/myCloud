package com.ff.mycloud.common.annotation;

import com.ff.mycloud.common.exceotion.RateLimitExceededException;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Lenovo
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 限流维度
     */
    enum Dimension {
        USER,    // 用户维度
        IP,      // IP维度
        GLOBAL   // 全局维度
    }

    /**
     * 限流维度
     */
    Dimension[] dimensions() default {Dimension.GLOBAL};

    /**
     * 每秒允许的请求数
     */
    double permitsPerSecond();

    /**
     * 获取令牌的超时时间 (毫秒)
     */
    long timeout() default 0;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 降级方法
     */
    String fallback() default "";

    /**
     * 异常类
     */
    Class<? extends Exception> exception() default RateLimitExceededException.class;
}
