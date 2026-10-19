package com.cloudrive.userService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * 用户表的领域服务，封装 MyBatis-Plus 的通用 CRUD 与角色相关查询。
 *
 * @author 飞飞
 * @date 2026/2/28
 */
public interface UserMapperService extends IService<User> {

    /**
     * 覆盖用户已有角色。普通用户与管理员的内置角色不允许被改动。
     *
     * @param userRole 用户与角色的关联
     */
    void updateUserRole(UserRole userRole);

    /**
     * 根据用户名查找用户。
     *
     * @param username 用户名
     * @return 用户，不存在或用户名为空时为空
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户。
     *
     * @param email 邮箱
     * @return 用户，不存在或邮箱为空时为空
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据用户 ID 查找用户。
     *
     * @param id 用户 ID
     * @return 用户，不存在时为空
     */
    Optional<User> findByUserId(Long id);

    /**
     * 根据 GitHub ID 查找用户。
     *
     * @param githubId GitHub 用户标识
     * @return 用户，不存在时返回 {@code null}
     */
    User findByGithubId(String githubId);

    /**
     * 查询用户的角色编码列表。
     *
     * @param id 用户 ID
     * @return 角色编码列表
     */
    List<String> getRoleById(long id);

    /**
     * 为用户新增一条角色关联。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    void setUserRole(Long userId, Long roleId);
}
