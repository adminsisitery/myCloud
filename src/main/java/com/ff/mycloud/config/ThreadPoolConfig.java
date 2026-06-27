package com.ff.mycloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * <p>
 * 配置用于文件上传的异步线程池，基于 IO 密集型场景设置核心线程数为 4（CPU核心数×2），
 * 最大线程数 8，队列容量 100，拒绝策略为抛出异常（AbortPolicy）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 创建文件上传专用线程池
     * <p>
     * 线程池配置：核心线程数 4（IO密集型，CPU核心数×2），最大线程数 8，
     * 队列容量 100，空闲线程存活时间 60 秒，拒绝策略为直接抛出异常。
     * </p>
     *
     * @return 配置好的 ThreadPoolTaskExecutor 实例
     */
    @Bean("fileUploadExecutor")
    public ThreadPoolTaskExecutor fileUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        //核心线程数 4 2*2 io密集型
        executor.setCorePoolSize(4);

        //最大线程数 8
        executor.setMaxPoolSize(8);

        //队列容量 100
        executor.setQueueCapacity(100);

        //空闲线程最大存活时间 60 秒
        executor.setKeepAliveSeconds(60);

        //线程名前缀，便于问题排查
        executor.setThreadNamePrefix("fileUploadExecutor-");

        //拒绝策略：队列满时直接抛出 RejectedExecutionException
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        //初始化线程池
        executor.initialize();

        return executor;
    }
}
