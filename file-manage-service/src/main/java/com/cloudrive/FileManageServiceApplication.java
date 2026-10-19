package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件管理微服务启动类
 * <p>
 * 放在 com.cloudrive 根包下，确保能扫描到 pan-common 中的公共 Bean。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileManageService.repositorys")
public class FileManageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManageServiceApplication.class, args);
    }
}
