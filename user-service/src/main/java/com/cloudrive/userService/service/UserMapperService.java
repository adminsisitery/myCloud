package com.cloudrive.userService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.userService.domain.po.User;


import java.util.List;
import java.util.Optional;

/**
 * 用户 Mapper 服务接口
 * <p>提供用户表的数据库操作，包括查询、角色管理等功能。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
public interface UserMapperService extends IService<User> {
    /**
     * 更新用户角色
     * <p>仅允许非管理员用户修改角色，管理员角色不可被修改。</p>
     *
     * @param userRole 用户角色实体，包含用户ID和角色ID
     */
    void updataUserRole(CommonConstants.UserRole userRole);

    /**
     * 根据用户名查找用户
     *
     * @param username 用户名
     * @return 用户实体Optional，不存在则为empty
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     *
     * @param email 邮箱地址
     * @return 用户实体Optional，不存在则为empty
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据用户ID查找用户
     *
     * @param id 用户ID
     * @return 用户实体Optional，不存在则为empty
     */
    Optional<User> findByUserId(Long id);

    /**
     * 根据GitHub ID查找用户
     *
     * @param githubId GitHub用户ID
     * @return 用户实体，不存在则返回null
     */
    User findByGithubId(String githubId);

    /**
     * 获取用户的角色编码列表
     *
     * @param id 用户ID
     * @return 角色编码列表
     */
    List<String> getRoleById(long id);

    /**
     * 为用户设置角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void SetUserRole(Long userId, Long roleId);
}
