package com.cloudrive.userService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.userService.domain.po.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper。
 * <p>SQL 定义在 resources/mapper/UserMapper.xml。</p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询用户拥有的角色编码列表。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，无角色时返回空列表
     */
    List<String> selectRoleCodeByUserId(@Param("userId") Long userId);

    /**
     * 为用户新增一条角色关联。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    void setUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 覆盖用户已有角色。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    void updateUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
