package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件下载服务启动类。
 * <p>
 * 放在 {@code com.cloudrive} 根包下，保证同时扫描到本服务的
 * {@code com.cloudrive.fileDownloadService} 与公共库 {@code com.cloudrive.pan_common}
 * （全局异常处理、限流切面、链路追踪等公共 bean）。
 * </p>
 *
 * @author 飞飞
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileDownloadService.repositorys")
public class FileDownloadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileDownloadServiceApplication.class, args);
    }
}
