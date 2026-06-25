package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.UserCenter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserCenterMapper extends BaseMapper<UserCenter> {

    @Update("""
            UPDATE user_center
            SET used_space = used_space + #{fileSize}
            WHERE user_id = #{userId}
              AND used_space + #{fileSize} <= total_space
            """)
    int reserveSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);

    @Update("""
            UPDATE user_center
            SET used_space = GREATEST(used_space - #{fileSize}, 0)
            WHERE user_id = #{userId}
            """)
    int releaseSpace(@Param("userId") long userId, @Param("fileSize") long fileSize);
}
