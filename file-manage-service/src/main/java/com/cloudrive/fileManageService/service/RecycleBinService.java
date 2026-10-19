package com.cloudrive.fileManageService.service;

import com.cloudrive.fileManageService.model.vo.TrashListVO;
import com.cloudrive.pan_common.result.Result;

import java.util.List;

/**
 * 回收站服务接口
 * <p>
 * 提供删除（单个/批量）、彻底删除、回收站列表、恢复与清空能力，
 * 语义对齐原单体 {@code DeleteFileServiceImpl}。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface RecycleBinService {

    /**
     * 删除单个文件/文件夹（软删除，进入回收站）
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    Result<String> deleteSingleFile(Long fileId);

    /**
     * 批量删除文件/文件夹（软删除，进入回收站）
     *
     * @param ids 逗号分隔的文件 ID
     * @return 操作结果
     */
    Result<String> batchDeleteFile(String ids);

    /**
     * 彻底删除文件/文件夹（物理删除 + 归还空间）
     *
     * @param fileIds 逗号分隔的文件 ID
     * @return 操作结果
     */
    Result<String> tureDeleteFile(String fileIds);

    /**
     * 查询回收站列表（含剩余保留天数）
     *
     * @return 回收站列表
     */
    List<TrashListVO> getTrashList();

    /**
     * 从回收站恢复文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    Result<String> restoreFile(Long fileId);

    /**
     * 清空回收站
     *
     * @return 操作结果
     */
    Result<String> cleanTrash();
}
