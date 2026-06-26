package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/28 16:14
 * @version 1.0
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {
    List<String> selectRoleCodeByUserId(@Param("userId") Long userId);

    void setUserRole(@Param("userId") Long userId,@Param("roleId") Long roleId);

    void updateUserRole(@Param("userId")Long userId,@Param("roleId") Long roleId);
}
