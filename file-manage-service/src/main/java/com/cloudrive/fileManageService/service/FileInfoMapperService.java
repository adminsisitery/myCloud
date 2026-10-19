package com.cloudrive.fileManageService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.fileManageService.model.entity.FileInfo;

import java.util.List;

/**
 * 文件信息查询服务接口
 * <p>
 * 封装 t_file_info 的常用查询（列表、子树、收藏、回收站等），
 * 查询语义与原单体 {@code FileInfoMapperService} 保持一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileInfoMapperService extends IService<FileInfo> {

    /**
     * 查询指定父目录下的文件/文件夹（未删除）
     *
     * @param currentUserId 当前用户 ID
     * @param parentId      父目录 ID（null 表示根目录）
     * @return 文件列表，文件夹在前
     */
    List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId);

    /**
     * 按文件 ID + 用户 ID 查询未删除文件
     *
     * @param fileId 文件 ID
     * @param userId 用户 ID
     * @return 文件信息，不存在返回 null
     */
    FileInfo findByIdAndUserId(Long fileId, Long userId);

    /**
     * 判断同级目录下是否存在同名文件夹
     *
     * @param parentId      父目录 ID
     * @param folderName    文件夹名称
     * @param currentUserId 当前用户 ID
     * @return true 表示已存在
     */
    Boolean existsFolder(Long parentId, String folderName, Long currentUserId);

    /**
     * 查询某目录路径下的整棵子树（未删除）
     *
     * @param localFilePath 目录本地路径
     * @param userId        用户 ID
     * @return 子树文件列表（含目录自身路径匹配项）
     */
    List<FileInfo> getSubTree(String localFilePath, Long userId);

    /**
     * 统计引用指定 SHA256 的未删除文件数量
     *
     * @param sha256Hash SHA256 哈希
     * @return 引用数量
     */
    Long countFiles(String sha256Hash);

    /**
     * 查询用户收藏的文件/文件夹
     *
     * @param currentUserId 当前用户 ID
     * @return 收藏列表
     */
    List<FileInfo> findStarredFiles(Long currentUserId);

    /**
     * 查询用户回收站中的文件/文件夹
     *
     * @param currentUserId 当前用户 ID
     * @return 回收站列表
     */
    List<FileInfo> getTrashFile(Long currentUserId);

    /**
     * 查询回收站中某目录路径下的整棵子树
     *
     * @param localFilePath 目录本地路径
     * @param currentUserId 当前用户 ID
     * @return 子树文件列表
     */
    List<FileInfo> getTrashSubTree(String localFilePath, Long currentUserId);

    /**
     * 按 ID 列表 + 用户 ID 查询未删除文件（用于批量删除归属校验）
     *
     * @param ids           文件 ID 列表
     * @param currentUserId 当前用户 ID
     * @return 匹配到的文件列表
     */
    List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId);
}
