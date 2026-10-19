package com.cloudrive.aiService.service;

import com.cloudrive.pan_common.result.Result;

/**
 * 文件夹服务接口（AI Agent 动作使用）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FolderService {

    /**
     * 创建文件夹
     *
     * @param parentId   父目录ID，null 或 0 表示根目录
     * @param folderName 文件夹名称
     * @return 操作结果
     */
    Result<String> createFolder(Long parentId, String folderName);
}
