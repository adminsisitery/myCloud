package com.cloudrive.fileManageService.service;

import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.pan_common.result.Result;

import java.util.List;

/**
 * 文件与文件夹管理服务接口
 * <p>
 * 提供列表、最近访问、重命名、移动、复制、收藏/取消收藏、收藏列表与最近访问记录，
 * 以及新建文件夹（见 {@link FolderService}）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileManageService {

    /**
     * 查询指定父目录下的文件/文件夹列表
     *
     * @param parentId 父目录 ID（0 或 null 表示根目录）
     * @return 文件列表
     */
    List<FileListVO> listFiles(Long parentId);

    /**
     * 查询最近访问的文件列表
     *
     * @return 最近访问列表
     */
    List<FileListVO> getRecentFiles();

    /**
     * 重命名文件/文件夹
     *
     * @param fileId      文件 ID
     * @param newFilename 新名称
     * @return 操作结果
     */
    Result<String> renameFile(Long fileId, String newFilename);

    /**
     * 移动文件/文件夹到目标目录
     *
     * @param fileId         文件 ID
     * @param targetParentId 目标父目录 ID（0 或 null 表示根目录）
     * @return 操作结果
     */
    Result<String> moveFile(Long fileId, Long targetParentId);

    /**
     * 复制文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    Result<String> copyFile(Long fileId);

    /**
     * 收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    Result<String> starFile(Long fileId);

    /**
     * 取消收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    Result<String> unstarFile(Long fileId);

    /**
     * 查询收藏列表
     *
     * @return 收藏列表
     */
    List<FileListVO> listStarredFiles();

    /**
     * 记录一次文件访问（由下载服务内部回调 / 最近访问列表使用）
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     */
    void recordAccess(Long userId, Long fileId);
}
