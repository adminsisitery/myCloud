package com.cloudrive.userService.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.userService.domain.dto.EmailSendDTO;
import com.cloudrive.userService.domain.dto.LoginDTO;
import com.cloudrive.userService.domain.dto.RegisterDTO;
import com.cloudrive.userService.domain.po.UserRole;
import com.cloudrive.userService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证与管理控制器
 * <p>
 * 提供用户的注册、登录、登出、验证码发送、强制登出以及角色变更功能。
 * 登录和注册接口有 IP 维度限流保护；管理员专用接口（强制登出、角色变更）需 @SaCheckRole("admin") 权限。
 * </p>
 *
 * @author 飞飞
 * @date 2026/1/11 21:43
 * @version 1.0
 */
@Validated
@RestController
@RequestMapping("/api/user")
public class UserController {
    UserService userService;

    /**
     * 构造器注入用户服务
     *
     * @param userService 用户服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户登录
     * <p>
     * 验证用户名和密码，成功后返回 Sa-Token 令牌。
     * 限流：每个 IP 每秒最多 5 次。
     * </p>
     *
     * @param loginDTO 包含用户名和密码的登录 DTO
     * @return Sa-Token 令牌
     */
    @PostMapping("/login")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 5.0, timeout = 1000)
    public Result login(@RequestBody @Valid LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }

    /**
     * 用户注册
     * <p>
     * 注册新用户账号，包含邮箱验证码校验。
     * 限流：每个 IP 每秒最多 2 次。
     * </p>
     *
     * @param registerDTO 包含用户名、密码、邮箱和验证码的注册 DTO
     * @return 操作结果
     */
    @PostMapping("/register")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public Result register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success();
    }

    /**
     * 发送邮箱验证码
     * <p>
     * 向指定邮箱发送注册验证码，限流：每个 IP 每秒最多 1 次。
     * </p>
     *
     * @param emailSendDTO 包含目标邮箱的 DTO
     * @return 操作结果
     */
    @PostMapping("/verification-code")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 1.0, timeout = 1000)
    public Result sendVerificationCode(@RequestBody @Validated EmailSendDTO emailSendDTO) {
        userService.sendVerificationCode(emailSendDTO.getEmail());
        return Result.success();
    }

    /**
     * 管理员强制登出指定用户
     * <p>
     * 仅限管理员角色（admin）调用，强制使目标用户的登录会话失效。
     * </p>
     *
     * @param userId 目标用户ID
     * @return 操作结果
     */
    @SaCheckRole("admin")
    @PostMapping("/forceLogout/{userId}")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<Void> forceLogout(@PathVariable Long userId) {
        userService.forceLogout(userId);
        return Result.success();
    }

    /**
     * 管理员修改用户角色
     * <p>
     * 仅限管理员角色（admin）调用，修改指定用户的角色权限。
     * </p>
     *
     * @param userRole 包含用户ID和目标角色的实体
     * @return 操作结果
     */
    @SaCheckRole("admin")
    @PostMapping("/changeRole")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<Void> changeRole(@RequestBody UserRole userRole){
       userService.changeUserRole(userRole);
       return Result.success();
    }

    /**
     * 用户登出
     * <p>
     * 使当前用户的登录会话失效（清除 Sa-Token 登录状态）。
     * </p>
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }
}
