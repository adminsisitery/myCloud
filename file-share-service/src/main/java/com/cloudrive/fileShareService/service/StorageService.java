package com.cloudrive.fileShareService.service;

import java.io.InputStream;

/**
 * 对象存储服务接口
 * <p>
 * 文件管理服务只需要：分享/文件夹打包下载时读取对象流、单文件分享下载时读取字节，
 * 以及彻底删除回收站文件时清理物理对象。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface StorageService {

    /**
     * 删除对象存储中的物理文件
     *
     * @param path 对象存储路径（t_file_info.path）
     */
    void deleteFile(String path);

    /**
     * 读取对象存储中的文件流（调用方负责关闭）
     *
     * @param path 对象存储路径
     * @return 文件输入流
     */
    InputStream getFile(String path);

    /**
     * 读取对象存储中的完整文件字节
     *
     * @param path 对象存储路径
     * @return 文件字节数组
     */
    byte[] downloadFile(String path);
}
