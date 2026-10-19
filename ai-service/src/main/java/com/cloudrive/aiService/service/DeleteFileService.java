package com.cloudrive.aiService.service;

/**
 * 回收站服务接口（AI Agent 动作使用）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface DeleteFileService {

    /**
     * 删除单个文件/文件夹（软删除 → 回收站）
     *
     * @param fileId 文件ID
     */
    void deleteSingleFile(Long fileId);

    /**
     * 从回收站恢复文件/文件夹
     *
     * @param fileId 文件ID
     */
    void restoreFile(Long fileId);
}
