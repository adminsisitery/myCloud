package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务微服务启动类
 * <p>
 * 集中承载原分散在各业务服务与单体中的定时任务：上传超时扫描、直传超时清理、
 * 分享过期标记、文件夹打包临时对象清理、回收站 30 天清理。
 * 不做业务 HTTP，不接网关路由，只暴露 actuator health/info。
 * </p>
 * <p>
 * 放在 {@code com.cloudrive} 根包下，才能同时扫描到本服务
 * {@code com.cloudrive.fileTaskService} 与公共库 {@code com.cloudrive.pan_common} 的 Bean。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileTaskService.repositorys")
@EnableScheduling
@EnableFeignClients(basePackages = "com.cloudrive.fileTaskService.client")
public class FileTaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileTaskServiceApplication.class, args);
    }
}
