package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.UserCenter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户中心Mapper接口
 * 功能描述：提供用户中心数据的CRUD操作，包括空间预留和释放
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public interface UserCenterMapper extends BaseMapper<UserCenter> {

    /**
     * 预留用户空间
     * 功能：原子性地为用户增加已用空间，仅在剩余空间足够时成功
     *
     * @param userId   用户ID
     * @param fileSize 文件大小（字节）
     * @return 受影响的行数，1表示预留成功，0表示空间不足或用户不存在
     */
    @Update("""
            UPDATE user_center
            SET used_space = used_space + #{fileSize}
            WHERE user_id = #{userId}
              AND used_space + #{fileSize} <= total_space
            """)
    int reserveSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);

    /**
     * 释放用户空间
     * 功能：减少用户已用空间，已用空间不会低于0
     *
     * @param userId   用户ID
     * @param fileSize 文件大小（字节）
     * @return 受影响的行数
     */
    @Update("""
            UPDATE user_center
            SET used_space = GREATEST(used_space - #{fileSize}, 0)
            WHERE user_id = #{userId}
            """)
    int releaseSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);
}
