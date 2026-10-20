package com.cloudrive.fileTaskService.repositorys;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件信息只读查询（回收站清理专用）
 * <p>
 * 直接写 SQL 而非依赖实体映射：t_file_info 的历史列名是驼峰
 * （{@code isDeleted}/{@code deletedAt}/{@code sha256Hash}），
 * 用简单返回值避开 MyBatis-Plus 的列名推导差异。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Mapper
public interface FileInfoMapper {

    /**
     * 查询回收站中超过保留期的文件 ID
     *
     * @param thirtyDaysAgo 截止时间
     * @return 文件 ID 列表
     */
    @Select("SELECT id FROM t_file_info WHERE isDeleted = 1 AND deletedAt < #{thirtyDaysAgo}")
    List<Long> findOverTimeFile(@Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    /**
     * 统计引用同一物理对象的行数（含回收站行）
     *
     * @param sha256Hash 文件哈希
     * @return 行数
     */
    @Select("SELECT COUNT(*) FROM t_file_info WHERE sha256Hash = #{sha256Hash}")
    long countByHash(@Param("sha256Hash") String sha256Hash);

    /**
     * 查询文件 OSS 路径
     *
     * @param id 文件 ID
     * @return OSS 对象 key
     */
    @Select("SELECT path FROM t_file_info WHERE id = #{id}")
    String findPathById(@Param("id") Long id);

    /**
     * 查询是否文件夹
     *
     * @param id 文件 ID
     * @return true=文件夹
     */
    @Select("SELECT isFolder FROM t_file_info WHERE id = #{id}")
    Boolean findIsFolderById(@Param("id") Long id);

    /**
     * 查询文件 SHA-256 哈希
     *
     * @param id 文件 ID
     * @return 哈希值
     */
    @Select("SELECT sha256Hash FROM t_file_info WHERE id = #{id}")
    String findSha256HashById(@Param("id") Long id);

    /**
     * 删除回收站记录
     *
     * @param id 文件 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM t_file_info WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
