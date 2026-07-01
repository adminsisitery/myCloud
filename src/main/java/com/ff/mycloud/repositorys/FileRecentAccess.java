package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.RecentAccess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 最近访问 Mapper
 * <p>记录用户访问文件的时间，支持去重更新和 JOIN 查询最近文件。</p>
 *
 * @author 飞飞
 * @date 2026/07/01
 */
@Mapper
public interface FileRecentAccess extends BaseMapper<RecentAccess> {

    /**
     * 记录文件访问（同用户+同文件去重，更新访问时间）
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     * @param now    访问时间
     */
    void recordAccess(@Param("userId") Long userId,
                      @Param("fileId") Long fileId,
                      @Param("now") LocalDateTime now);

    /**
     * 查询最近访问的 N 个文件（JOIN t_file_info 直接返回 FileInfo）
     *
     * @param userId 用户ID
     * @param limit  返回条数
     * @return 文件信息列表
     */
    List<FileInfo> getRecentFiles(@Param("userId") Long userId,
                                  @Param("limit") int limit);
}
