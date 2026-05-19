package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.EmailUtil;
import com.ff.mycloud.model.dto.EmailSendDTO;
import com.ff.mycloud.model.dto.LoginDTO;
import com.ff.mycloud.model.dto.RegisterDTO;
import com.ff.mycloud.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/11 21:43
 * @version 1.0
 */
@Validated
@RestController
@RequestMapping("/api/user")
public class UserController {
    UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 5.0, timeout = 1000)
    public Result login(@RequestBody @Valid LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }
    @PostMapping("/register")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public Result register(@RequestBody @Valid RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success();
    }
    @PostMapping("/verification-code")
    @RateLimit(dimensions = { RateLimit.Dimension.IP }, permitsPerSecond = 1.0, timeout = 1000)
    public Result sendVerificationCode(@RequestBody @Validated EmailSendDTO emailSendDTO) {
        userService.sendVerificationCode(emailSendDTO.getEmail());
        return Result.success();
    }
    /**
     * 强制登出
     */
    @PostMapping("/forceLogout/{userId}")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<Void> forceLogout(@PathVariable Long userId) {
        userService.forceLogout(userId);
        return Result.success();
    }
    /**
     * 登出
     */
    @PostMapping("/logout")
    @RateLimit(dimensions = { RateLimit.Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }
}
