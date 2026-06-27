package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper接口
 * 功能描述：提供用户数据的CRUD操作，包括角色查询与角色分配管理
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户ID查询角色编码列表
     * 功能：获取指定用户所拥有的所有角色编码
     *
     * @param userId 用户ID
     * @return 角色编码字符串列表，若用户无角色则返回空列表
     */
    List<String> selectRoleCodeByUserId(@Param("userId") Long userId);

    /**
     * 为用户设置角色
     * 功能：为用户分配一个角色，若用户已有角色则新增关联
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void setUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 更新用户角色
     * 功能：更新用户已有的角色分配，将用户角色替换为新的角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void updateUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
