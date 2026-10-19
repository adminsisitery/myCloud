package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件与文件夹管理微服务启动类
 * <p>
 * 负责文件/文件夹元数据管理：列表、新建文件夹、重命名、移动、复制、收藏、
 * 最近访问与回收站（软删除/批量删除/恢复/彻底删除/清空）。
 * 上传与单文件下载分别由 file-upload-service、file-download-service 承担。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan("com.cloudrive.fileManageService.repositorys")
public class FileManageServiceApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FileManageServiceApplication.class, args);
    }
}
