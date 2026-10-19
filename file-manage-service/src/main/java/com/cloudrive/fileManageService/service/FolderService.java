package com.cloudrive.fileManageService.service;

import com.cloudrive.pan_common.result.Result;

/**
 * 文件夹服务接口
 * <p>
 * 目前仅包含新建文件夹；文件夹的列表/重命名/移动/复制/删除由
 * {@link FileManageService}、{@link RecycleBinService} 统一处理。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FolderService {

    /**
     * 新建文件夹
     *
     * @param parentId   父目录 ID（0 或 null 表示根目录）
     * @param folderName 文件夹名称
     * @return 操作结果
     */
    Result<String> createFolder(Long parentId, String folderName);
}
