package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.FileInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件信息 Mapper 服务接口
 * <p>提供文件信息的数据库操作，包括查询、更新、统计等功能。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
public interface FileInfoMapperService extends IService<FileInfo> {
    /**
     * 根据文件哈希值和用户ID查找未被删除的文件列表
     *
     * @param hash   文件的SHA-256哈希值
     * @param userId 用户ID
     * @return 匹配的文件信息列表
     */
    List<FileInfo> findByHashAndUserId(String hash, Long userId);

    /**
     * 根据父目录ID和用户ID查找文件列表（按是否为文件夹排序）
     *
     * @param currentUserId 当前用户ID
     * @param parentId      父目录ID，为null时查询根目录
     * @return 文件信息列表，文件夹排在前面
     */
    List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId);

    /**
     * 根据文件ID更新文件名
     *
     * @param fileId      文件ID
     * @param newFilename 新文件名
     * @param now         更新时间
     * @return true表示更新成功
     */
    boolean UpdateFileNameByFileId(Long fileId, String newFilename, LocalDateTime now);

    /**
     * 统计指定哈希值的文件引用数量
     *
     * @param sha256Hash 文件的SHA-256哈希值
     * @return 引用该哈希值的文件数量
     */
    Long countFiles(String sha256Hash);

    /**
     * 根据关键词在当前用户的文件中模糊搜索
     *
     * @param keyword        搜索关键词
     * @param currentUserId  当前用户ID
     * @return 匹配的文件信息列表
     */
    List<FileInfo> findBykeyWord(String keyword, Long currentUserId);

    /**
     * 根据文件ID和用户ID查找未被删除的单个文件
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息实体，不存在则返回null
     */
    FileInfo findByIdAndUserId(Long fileId, Long userId);

    /**
     * 判断指定父目录下是否存在同名文件夹
     *
     * @param parentId       父目录ID
     * @param folderName     文件夹名称
     * @param currentUserId  当前用户ID
     * @return true表示存在同名文件夹
     */
    Boolean existsFolder(Long parentId, String folderName, Long currentUserId);

    /**
     * 判断指定父目录ID是否存在且为文件夹
     *
     * @param parentId      父目录ID
     * @param currentUserId 当前用户ID
     * @return true表示父目录存在
     */
    Boolean existsParentId(Long parentId, Long currentUserId);

    /**
     * 获取文件树结构
     *
     * @param fileId 文件ID
     */
    void getFileTree(Long fileId);

    /**
     * 获取指定路径下的所有子节点（文件和文件夹）
     * <p>通过本地文件路径前缀匹配查询子树，支持路径结尾带/或不带/。</p>
     *
     * @param localFilePath 本地文件路径
     * @param userId        用户ID
     * @return 子树中的所有文件信息列表
     */
    public List<FileInfo> getSubTree(String localFilePath, Long userId) ;

    /**
     * 根据ID列表和用户ID批量查找文件
     *
     * @param ids            文件ID列表
     * @param currentUserId  当前用户ID
     * @return 匹配的文件信息列表
     */
    List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId);

    List<FileInfo> getTrashFile(Long currentUserId);

    /**
     * 获取回收站中指定目录下的所有子节点（含自身）
     * <p>与 getSubTree 对应，但查询 isDeleted=true 的回收站文件。</p>
     *
     * @param localFilePath 本地文件路径
     * @param currentUserId 当前用户ID
     * @return 回收站中的子文件列表
     */
    List<FileInfo> getTrashSubTree(String localFilePath, Long currentUserId);

    List<FileInfo> findOverTimeFile(Long currentUserId);

    /**
     * 查询当前用户的所有收藏文件
     *
     * @param currentUserId 当前用户ID
     * @return 收藏的文件列表
     */
    List<FileInfo> findStarredFiles(Long currentUserId);
}
