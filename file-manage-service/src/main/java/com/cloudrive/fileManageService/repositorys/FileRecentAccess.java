package com.cloudrive.fileManageService.repositorys;

import com.cloudrive.fileManageService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 最近访问记录 Mapper 接口
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface FileRecentAccess {

    /**
     * 记录一次文件访问（同一用户同一文件只保留最新一条）
     *
     * @param userId     用户 ID
     * @param fileId     文件 ID
     * @param accessedAt 访问时间
     */
    @Insert("INSERT INTO t_recent_access (user_id, file_id, accessed_at) " +
            "VALUES (#{userId}, #{fileId}, #{accessedAt}) " +
            "ON DUPLICATE KEY UPDATE accessed_at = #{accessedAt}")
    void recordAccess(@Param("userId") Long userId, @Param("fileId") Long fileId,
                      @Param("accessedAt") LocalDateTime accessedAt);

    /**
     * 查询用户最近访问的文件（关联 t_file_info，过滤已删除文件）
     *
     * @param userId 用户 ID
     * @param limit  返回条数
     * @return 最近访问文件列表
     */
    List<FileInfo> getRecentFiles(@Param("userId") Long userId, @Param("limit") int limit);
}
