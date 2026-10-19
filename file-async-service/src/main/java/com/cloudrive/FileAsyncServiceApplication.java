package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步处理微服务启动类
 * <p>
 * 只做后台异步任务：消费 RabbitMQ 消息完成 ES 索引同步、PG 向量同步、操作日志落库。
 * 不对外提供业务 HTTP 接口（无网关路由），仅暴露 actuator health/info。
 * 启动类放在 {@code com.cloudrive} 根包，才能扫描到 pan-common 的公共 Bean。
 * </p>
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileAsyncService.repositorys")
@EnableRetry
@EnableScheduling
public class FileAsyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileAsyncServiceApplication.class, args);
    }
}
