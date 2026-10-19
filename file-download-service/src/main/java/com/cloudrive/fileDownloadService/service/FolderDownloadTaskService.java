package com.cloudrive.fileDownloadService.service;

import com.cloudrive.fileDownloadService.model.vo.FolderDownloadTaskVO;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;

/**
 * 文件夹异步打包任务服务（下载服务侧：创建 / 查询 / 取消）
 * <p>
 * 打包执行由 file-async-service 消费 MQ 完成；本服务只负责创建任务、
 * 写入共享 Redis、投递 MQ，以及查询/取消任务记录。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FolderDownloadTaskService {

    /**
     * 创建文件夹打包任务
     *
     * @param folderId 文件夹ID
     * @return 任务视图
     */
    FolderDownloadTaskVO create(Long folderId);

    /**
     * 查询当前用户的任务
     *
     * @param taskId 任务ID
     * @return 任务视图
     */
    FolderDownloadTaskVO getOwnedTask(String taskId);

    /**
     * 获取可下载的已完成任务（校验归属、状态与过期时间）
     *
     * @param taskId 任务ID
     * @return 任务对象
     */
    FolderDownloadTask getDownloadableTask(String taskId);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    void cancel(String taskId);
}
