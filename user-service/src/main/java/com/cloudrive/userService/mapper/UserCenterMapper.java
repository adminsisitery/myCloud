package com.cloudrive.userService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.userService.domain.po.UserCenter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户中心（存储配额）表 Mapper。
 * <p>
 * 与 {@code com.cloudrive.userService.convert.UserCenterConverter} 区分：
 * 后者是 MapStruct 转换器，本类是 MyBatis 数据访问接口。
 * 拆分前两者同名（分别在 mapper 与 repositorys 包下），迁移时被合并进同一个文件，
 * 于是同一个接口既被当成 BaseMapper 又被当成转换器，永远无法编译。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface UserCenterMapper extends BaseMapper<UserCenter> {

    /**
     * 原子地为用户预留空间：只有剩余空间足够时才增加已用空间。
     *
     * @param userId   用户 ID
     * @param fileSize 文件大小（字节）
     * @return 受影响行数，1 表示成功，0 表示空间不足或用户不存在
     */
    @Update("""
            UPDATE user_center
            SET used_space = used_space + #{fileSize}
            WHERE user_id = #{userId}
              AND used_space + #{fileSize} <= total_space
            """)
    int reserveSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);

    /**
     * 释放用户空间，已用空间不会降到 0 以下。
     *
     * @param userId   用户 ID
     * @param fileSize 文件大小（字节）
     * @return 受影响行数
     */
    @Update("""
            UPDATE user_center
            SET used_space = GREATEST(used_space - #{fileSize}, 0)
            WHERE user_id = #{userId}
            """)
    int releaseSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);
}
