package com.cloudrive.fileService.config;

import com.cloudrive.pan_common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文件上传线程池配置
 * <p>
 * 普通上传的耗时动作（写临时文件、推 OSS、落库）放在异步线程池里执行，
 * 不占住 Tomcat 请求线程；线程名前缀沿用单体的 {@code fileUploadExecutor-}，
 * 便于日志按上传任务检索。MDC 通过 {@link MdcTaskDecorator} 传递到异步线程，
 * 保证 traceId 不丢失。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 普通文件上传专用线程池。
     * <p>队列有界（100），满载时直接拒绝并抛出异常，由上传任务服务捕获后
     * 归还预占空间并把任务标记为失败，避免无界队列把内存撑爆。</p>
     *
     * @return 上传线程池
     */
    @Bean("fileUploadExecutor")
    public ThreadPoolTaskExecutor fileUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("fileUploadExecutor-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
