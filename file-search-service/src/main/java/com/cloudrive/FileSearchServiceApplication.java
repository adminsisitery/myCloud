package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件搜索微服务启动类
 * <p>
 * 启动类放在 {@code com.cloudrive} 根包下，保证能扫描到 pan-common 的限流切面等公共组件。
 * 搜索策略由 {@code file.search} 配置决定（Mysql 默认 / ES）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileSearchService.repositorys")
public class FileSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileSearchServiceApplication.class, args);
    }
}
