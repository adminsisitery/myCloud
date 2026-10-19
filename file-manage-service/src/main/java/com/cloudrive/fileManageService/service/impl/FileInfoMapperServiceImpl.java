package com.cloudrive.fileManageService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.repositorys.FileInfoMapper;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件信息查询服务实现类
 * <p>
 * 基于 MyBatis-Plus 实现 t_file_info 的常用查询，语义对齐原单体实现。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FileInfoMapperServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileInfoMapperService {

    /**
     * 查询指定父目录下的文件/文件夹（未删除）
     *
     * @param currentUserId 当前用户 ID
     * @param parentId      父目录 ID（null 表示根目录）
     * @return 文件列表，文件夹在前
     */
    @Override
    public List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false);
        if (parentId == null) {
            wrapper.isNull(FileInfo::getParentId);
        } else {
            wrapper.eq(FileInfo::getParentId, parentId);
        }
        wrapper.orderByDesc(FileInfo::getIsFolder)
                .orderByDesc(FileInfo::getCreatedAt);
        return list(wrapper);
    }

    /**
     * 按文件 ID + 用户 ID 查询未删除文件
     *
     * @param fileId 文件 ID
     * @param userId 用户 ID
     * @return 文件信息，不存在返回 null
     */
    @Override
    public FileInfo findByIdAndUserId(Long fileId, Long userId) {
        return lambdaQuery()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .one();
    }

    /**
     * 判断同级目录下是否存在同名文件夹
     *
     * @param parentId      父目录 ID
     * @param folderName    文件夹名称
     * @param currentUserId 当前用户 ID
     * @return true 表示已存在
     */
    @Override
    public Boolean existsFolder(Long parentId, String folderName, Long currentUserId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getIsFolder, true)
                .eq(FileInfo::getFilename, folderName);
        if (parentId == null) {
            wrapper.isNull(FileInfo::getParentId);
        } else {
            wrapper.eq(FileInfo::getParentId, parentId);
        }
        return baseMapper.exists(wrapper);
    }

    /**
     * 查询某目录路径下的整棵子树（未删除）
     *
     * @param localFilePath 目录本地路径
     * @param userId        用户 ID
     * @return 子树文件列表
     */
    @Override
    public List<FileInfo> getSubTree(String localFilePath, Long userId) {
        String matchPath = normalizeDirectory(localFilePath);
        String escaped = escapeLike(matchPath);
        return lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .apply("(local_file_path = {0} OR local_file_path LIKE {1} ESCAPE '!')",
                        matchPath, escaped + "%")
                .list();
    }

    /**
     * 统计引用指定 SHA256 的未删除文件数量
     *
     * @param sha256Hash SHA256 哈希
     * @return 引用数量
     */
    @Override
    public Long countFiles(String sha256Hash) {
        return lambdaQuery()
                .eq(FileInfo::getSha256Hash, sha256Hash)
                .eq(FileInfo::getIsDeleted, false)
                .count();
    }

    /**
     * 查询用户收藏的文件/文件夹
     *
     * @param currentUserId 当前用户 ID
     * @return 收藏列表
     */
    @Override
    public List<FileInfo> findStarredFiles(Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getIsStarred, true)
                .orderByDesc(FileInfo::getStarredAt)
                .list();
    }

    /**
     * 查询用户回收站中的文件/文件夹
     *
     * @param currentUserId 当前用户 ID
     * @return 回收站列表
     */
    @Override
    public List<FileInfo> getTrashFile(Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, true)
                .isNotNull(FileInfo::getDeletedAt)
                .orderByDesc(FileInfo::getDeletedAt)
                .list();
    }

    /**
     * 查询回收站中某目录路径下的整棵子树
     *
     * @param localFilePath 目录本地路径
     * @param currentUserId 当前用户 ID
     * @return 子树文件列表
     */
    @Override
    public List<FileInfo> getTrashSubTree(String localFilePath, Long currentUserId) {
        String matchPath = normalizeDirectory(localFilePath);
        String escaped = escapeLike(matchPath);
        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, true)
                .apply("(local_file_path = {0} OR local_file_path LIKE {1} ESCAPE '!')",
                        matchPath, escaped + "%")
                .list();
    }

    /**
     * 按 ID 列表 + 用户 ID 查询未删除文件
     *
     * @param ids           文件 ID 列表
     * @param currentUserId 当前用户 ID
     * @return 匹配到的文件列表
     */
    @Override
    public List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId) {
        return lambdaQuery()
                .in(FileInfo::getId, ids)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }

    /**
     * 按文件名关键词模糊搜索当前用户的未删除文件（MySQL LIKE 策略）
     *
     * @param keyword       关键词
     * @param currentUserId 当前用户 ID
     * @return 匹配的文件列表
     */
    @Override
    public List<FileInfo> findBykeyWord(String keyword, Long currentUserId) {
        return lambdaQuery()
                .like(FileInfo::getFilename, keyword)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getUserId, currentUserId)
                .list();
    }

    /**
     * 目录路径规范化：统一以 "/" 结尾，避免前缀误匹配
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    private String normalizeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * 转义 LIKE 通配符（使用 "!" 作为转义符）
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
