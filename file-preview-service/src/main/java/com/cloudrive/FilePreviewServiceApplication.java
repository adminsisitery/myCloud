package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件预览服务启动类
 * <p>
 * 校验文件归属与预览类型后，生成 5 分钟有效的 OSS 预签名 URL，
 * 前端据此在线预览图片/视频/音频/PDF/文本文件。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan("com.cloudrive.filePreviewService.repositorys")
public class FilePreviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilePreviewServiceApplication.class, args);
    }
}
