package com.cloudrive.fileDownloadService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.fileDownloadService.model.entity.FileInfo;

import java.util.List;

/**
 * 文件信息查询服务（下载服务只读）
 * <p>
 * 除单文件下载的归属校验外，文件夹打包还需要按 local_file_path 前缀
 * 取出整棵子树。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileInfoMapperService extends IService<FileInfo> {

    /**
     * 按文件ID + 用户ID查询未删除的文件/文件夹
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息，不存在返回 null
     */
    FileInfo findByIdAndUserId(Long fileId, Long userId);

    /**
     * 查询目录下整棵子树（local_file_path 前缀匹配，结果不含已删除文件）
     *
     * @param localFilePath 目录路径
     * @param userId        用户ID
     * @return 子树文件列表
     */
    List<FileInfo> getSubTree(String localFilePath, Long userId);
}
