package com.cloudrive.fileAsyncService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;

import java.util.List;

/**
 * 文件信息查询服务（只读）
 */
public interface FileInfoMapperService extends IService<FileInfo> {

    /**
     * 按文件ID + 用户ID查询未删除的文件
     */
    FileInfo findByIdAndUserId(Long fileId, Long userId);

    /**
     * 查询目录下整棵子树（local_file_path 前缀匹配，结果不含已删除文件）
     */
    List<FileInfo> getSubTree(String localFilePath, Long userId);
}
