package com.ff.mycloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("fileUploadExecutor")
    public ThreadPoolTaskExecutor fileUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        //核心线程数 4 2*2 io密集型
        executor.setCorePoolSize(4);

        //最大线程数
        executor.setMaxPoolSize(8);

        //队列容量
        executor.setQueueCapacity(100);

        //最大存货时间
        executor.setKeepAliveSeconds(60);

        //线程前缀名
        executor.setThreadNamePrefix("fileUploadExecutor-");

        //拒绝策略 直接抛出异常
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        //初始化
        executor.initialize();

        return executor;
    }
}
