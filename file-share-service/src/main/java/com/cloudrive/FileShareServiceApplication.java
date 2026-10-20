package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件分享微服务启动类
 * <p>
 * 放在 com.cloudrive 根包下，确保能扫描到 pan-common 中的公共 Bean。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileShareService.repositorys")
public class FileShareServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileShareServiceApplication.class, args);
    }
}
