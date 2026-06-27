package com.ff.mycloud.service;

import com.ff.mycloud.model.dto.LoginDTO;
import com.ff.mycloud.model.dto.RegisterDTO;
import com.ff.mycloud.model.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户服务接口
 * <p>提供用户登录、注册、登出、角色管理等核心业务功能。</p>
 *
 * @author 飞飞
 * @date 2026/1/11
 */
public interface UserService {
    /**
     * 用户登录
     * <p>三层防护：限流、IP维度计数、账户维度渐进退避+硬锁。</p>
     *
     * @param loginDTO 登录请求参数（用户名和密码）
     * @return 登录成功返回的token
     */
    String login(LoginDTO loginDTO);

    /**
     * 用户注册
     *
     * @param registerDTO 注册请求参数（用户名、密码、邮箱、验证码）
     */
    void register(RegisterDTO registerDTO);

    /**
     * 发送邮箱验证码
     *
     * @param email 目标邮箱地址
     */
    void sendVerificationCode(@NotBlank @Email String email);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 强制下线指定用户
     *
     * @param userId 要强制下线的用户ID
     */
    void forceLogout(Long userId);

    /**
     * 为用户添加角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void addRole(Long userId, Long roleId);

    /**
     * 修改用户角色
     *
     * @param userRole 用户角色实体
     */
    void changeUserRole(UserRole userRole);
}
