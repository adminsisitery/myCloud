package com.cloudrive.fileManageService.service;

/**
 * 对象存储服务接口
 * <p>
 * 文件管理服务只在彻底删除回收站文件时清理物理对象，因此仅暴露删除能力。
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
}
