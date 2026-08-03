package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.ai.model.entity.QueryData;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件信息 Mapper 服务实现类
 * <p>基于MyBatis-Plus实现文件信息的数据库操作，包括增删改查、秒传检测、子树查询等。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 * @version 1.0
 */


@Service
public class FileInfoMapperServiceImpl
        extends ServiceImpl<FileInfoMapper,FileInfo>
        implements FileInfoMapperService {

    private final FileMapper fileMapper;

    public FileInfoMapperServiceImpl(FileMapper fileMapper) {
        this.fileMapper = fileMapper;
    }

    /**
     * 根据hash和用户id查找没有被删除的文件
     *
     * @param hash   文件的SHA-256哈希值
     * @param userId 用户ID
     * @return 匹配的文件信息列表
     */
    @Override
    public List<FileInfo> findByHashAndUserId(String hash, Long userId){
        return lambdaQuery()
                .eq(FileInfo::getSha256Hash, hash)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }

    /**
     * 根据父目录ID和用户ID查找文件列表（文件夹排在前面）
     *
     * @param currentUserId 当前用户ID
     * @param parentId      父目录ID，为null时查询根目录
     * @return 文件信息列表
     */
    @Override
    public List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId) {

        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(parentId != null, FileInfo::getParentId, parentId)
                .orderBy(true,false,FileInfo::getIsFolder)
                .isNull(parentId == null, FileInfo::getParentId)
                .list();
    }

    /**
     * 根据文件ID更新文件名和修改时间
     *
     * @param fileId      文件ID
     * @param newFilename 新文件名
     * @param now         更新时间
     * @return true表示更新成功
     */
    @Override
    public boolean UpdateFileNameByFileId(Long fileId, String newFilename, LocalDateTime now) {
        return lambdaUpdate().eq(FileInfo::getId, fileId)
                .set(FileInfo::getFilename, newFilename)
                .set(FileInfo::getOriginalFilename, newFilename)
                .set(FileInfo::getUpdatedAt,now)
                .update();
    }

    /**
     * 统计指定哈希值的文件引用数量
     *
     * @param sha256Hash 文件的SHA-256哈希值
     * @return 引用该哈希值的文件数量
     */
    @Override
    public Long countFiles(String sha256Hash) {
        return lambdaQuery()
                .eq(FileInfo::getSha256Hash,sha256Hash)
                .eq(FileInfo::getIsDeleted, false)
                .count();
    }

    /**
     * 根据关键词模糊搜索文件名
     *
     * @param keyword        搜索关键词
     * @param currentUserId  当前用户ID
     * @return 匹配的文件信息列表
     */
    @Override
    public List<FileInfo> findBykeyWord(String keyword, Long currentUserId) {
        return lambdaQuery()
                .like(FileInfo::getFilename,keyword)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getUserId, currentUserId)
                .list();
    }

    /**
     * 根据文件ID和用户ID查找单个文件
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息实体，不存在则返回null
     */
    @Override
    public FileInfo findByIdAndUserId(Long fileId, Long userId) {
        return lambdaQuery().eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId,userId)
                .eq(FileInfo::getIsDeleted, false)
                .one();
    }

    /**
     * 判断指定父目录下是否存在同名文件夹
     *
     * @param parentId       父目录ID
     * @param folderName     文件夹名称
     * @param currentUserId  当前用户ID
     * @return true表示存在同名文件夹
     */
    @Override
    public Boolean existsFolder(Long parentId, String folderName, Long currentUserId) {

        return lambdaQuery()
                .eq(FileInfo::getIsDeleted,false)
                .eq(FileInfo::getParentId,parentId)
                .eq(FileInfo::getIsFolder,true)
                .eq(FileInfo::getFilename,folderName)
                .eq(FileInfo::getUserId,currentUserId)
                .exists();
    }

    /**
     * 判断指定父目录ID是否存在且为当前用户的文件夹
     *
     * @param parentId       父目录ID
     * @param currentUserId  当前用户ID
     * @return true表示父目录存在
     */
    @Override
    public Boolean existsParentId(Long parentId, Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId,currentUserId)
                .eq(FileInfo::getIsFolder,true)
                .eq(FileInfo::getId,parentId)
                .exists();

    }

    /**
     * 获取文件树结构
     *
     * @param fileId 文件ID
     */
    @Override
    public void getFileTree(Long fileId) {

    }

    /**
     * 获取指定路径下的所有子节点
     * <p>标准化路径匹配：解决路径结尾/导致的匹配失败问题。
     * 同时匹配根节点本身和所有前缀匹配的子节点。</p>
     *
     * @param localFilePath 本地文件路径
     * @param userId        用户ID
     * @return 子树中的所有文件信息列表
     */
    @Override
    public List<FileInfo> getSubTree(String localFilePath, Long userId) {
        // 标准化路径匹配：解决路径结尾/导致的匹配失败问题
        String matchPath = localFilePath;
        // 确保拼接后是 "路径/"，而非 "路径//" 或 "路径"
        if (!matchPath.endsWith("/")) {
            matchPath = matchPath + "/";
        }
        final String s = matchPath;
        return lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .and(w -> w
                        // 匹配根节点本身（比如文件夹2）
                        .eq(FileInfo::getLocalFilePath, localFilePath)
                        // 匹配所有子节点（比如文件夹2/xxx.txt）
                        .or()
                        .likeRight(FileInfo::getLocalFilePath, s)
                )
                .list();
    }

    /**
     * 根据ID列表批量查找文件
     *
     * @param ids           文件ID列表
     * @param currentUserId 当前用户ID
     * @return 匹配的文件信息列表
     */
    @Override
    public List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId) {
        return lambdaQuery().in(FileInfo::getId,ids)
                .eq(FileInfo::getUserId,currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }

    @Override
    public List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId, QueryData qd) {
        var wrapper = lambdaQuery().in(FileInfo::getId, ids)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false);

        if (qd.getDateFrom() != null && !qd.getDateFrom().isBlank()) {
            try { wrapper.ge(FileInfo::getCreatedAt, LocalDate.parse(qd.getDateFrom()).atStartOfDay()); }
            catch (Exception ignored) {}
        }
        if (qd.getDateTo() != null && !qd.getDateTo().isBlank()) {
            try { wrapper.le(FileInfo::getCreatedAt, LocalDate.parse(qd.getDateTo()).atTime(LocalTime.MAX)); }
            catch (Exception ignored) {}
        }
        if (qd.getFileType() != null && !qd.getFileType().isBlank()) {
            wrapper.eq(FileInfo::getFileSuffix, qd.getFileType().toLowerCase());
        }
        if (qd.getMinSize() != null && qd.getMinSize() > 0) {
            wrapper.ge(FileInfo::getFileSize, qd.getMinSize());
        }
        if (qd.getMaxSize() != null && qd.getMaxSize() > 0) {
            wrapper.le(FileInfo::getFileSize, qd.getMaxSize());
        }

        return wrapper.list();
    }

    @Override
    public List<FileInfo> getTrashFile(Long currentUserId) {
        return lambdaQuery().eq(FileInfo::getUserId,currentUserId)
                .eq(FileInfo::getIsDeleted,true)
                .isNotNull(FileInfo::getDeletedAt)
                .list();
    }

    @Override
    public List<FileInfo> getTrashSubTree(String localFilePath, Long currentUserId) {
        String matchPath = localFilePath;
        if (!matchPath.endsWith("/")) {
            matchPath = matchPath + "/";
        }
        final String s = matchPath;
        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, true)
                .and(w -> w
                        .eq(FileInfo::getLocalFilePath, localFilePath)
                        .or()
                        .likeRight(FileInfo::getLocalFilePath, s)
                )
                .list();
    }

    @Override
    public List<FileInfo> findOverTimeFile(Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId,currentUserId)
                .eq(FileInfo::getIsDeleted,true)
                .lt(FileInfo::getDeletedAt, LocalDateTime.now().minusDays(30))
                .list();
    }

    @Override
    public List<FileInfo> findStarredFiles(Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getIsStarred, true)
                .orderByDesc(FileInfo::getStarredAt)
                .list();
    }

    @Override
    public List<FileListVO> findbyQD(QueryData queryData) {
        return findbyQD(queryData, UserContext.getCurrentUserId());
    }

    @Override
    public List<FileListVO> findbyQD(QueryData queryData, Long userId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getUserId, userId);
        wrapper.eq(FileInfo::getIsDeleted, false);
        wrapper.eq(FileInfo::getIsFolder, false);

        if (queryData.getFileName() != null && !queryData.getFileName().isBlank()) {
            wrapper.and(w -> w
                    .like(FileInfo::getFilename, queryData.getFileName())
                    .or()
                    .like(FileInfo::getOriginalFilename, queryData.getFileName()));
        }
        if (queryData.getDateFrom() != null && !queryData.getDateFrom().isBlank()) {
            try {
                wrapper.ge(FileInfo::getCreatedAt,
                        LocalDate.parse(queryData.getDateFrom()).atStartOfDay());
            } catch (Exception ignored) {}
        }
        if (queryData.getDateTo() != null && !queryData.getDateTo().isBlank()) {
            try {
                wrapper.le(FileInfo::getCreatedAt,
                        LocalDate.parse(queryData.getDateTo()).atTime(LocalTime.MAX));
            } catch (Exception ignored) {}
        }
        if (queryData.getFileType() != null && !queryData.getFileType().isBlank()) {
            wrapper.eq(FileInfo::getFileSuffix, queryData.getFileType().toLowerCase());
        }
        if (queryData.getMinSize() != null && queryData.getMinSize() > 0) {
            wrapper.ge(FileInfo::getFileSize, queryData.getMinSize());
        }
        if (queryData.getMaxSize() != null && queryData.getMaxSize() > 0) {
            wrapper.le(FileInfo::getFileSize, queryData.getMaxSize());
        }

        List<FileInfo> files = this.list(wrapper);
        return files.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

}
