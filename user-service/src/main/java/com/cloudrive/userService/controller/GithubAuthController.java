package com.cloudrive.userService.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.userService.config.GithubAuthConfig;
import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.service.UserCenterMapperService;
import com.cloudrive.userService.service.UserMapperService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Github OAuth 认证控制器
 * <p>
 * 处理 Github 第三方登录的完整流程：跳转 Github 授权页面 -> Github 回调 ->
 * 获取用户信息 -> 新用户自动注册/老用户登录 -> 生成 Sa-Token 并重定向至前端。
 * 包含限流保护，登录和回调接口均限制为约 5 次/分钟。
 * </p>
 * <p>
 * 未配置 oauth.github 凭据时，接口降级为重定向回登录页并提示，不影响账号密码登录。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/4 21:50
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth/github")
@Slf4j
@RequiredArgsConstructor
public class GithubAuthController {

    /** 新用户默认可用空间：10MB */
    private static final long DEFAULT_TOTAL_SPACE = 10485760L;

    private final GithubAuthConfig githubAuthConfig;
    private final UserMapperService userMapperService;
    private final UserCenterMapperService userCenterMapperService;

    /** 前端站点地址，登录完成后跳回该地址 */
    @Value("${oauth.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /** 懒加载的授权请求对象，凭据配置完整后首次使用时构建 */
    private volatile AuthRequest githubAuthRequest;

    /**
     * 跳转 Github 授权页面（步骤1）
     * <p>
     * 重定向用户到 Github OAuth 授权页面。限流：每个 IP + 全局维度约 5 次/分钟。
     * 超出限制时触发 callbackFallback 降级方法。
     * </p>
     *
     * @param response HTTP 响应对象，用于重定向
     * @throws IOException 重定向时可能抛出的 IO 异常
     */
    @GetMapping("/login")
    @RateLimit(
            dimensions = {RateLimit.Dimension.IP, RateLimit.Dimension.GLOBAL}, // IP+全局维度
            permitsPerSecond = 0.083, // 约5次/分钟（5/60≈0.083）
            timeout = 0, // 无等待，直接拒绝
            fallback = "callbackFallback" // 降级方法
    )
    public void login(HttpServletResponse response) throws IOException {
        AuthRequest request = resolveRequest();
        if (request == null) {
            redirectNotConfigured(response);
            return;
        }
        // 重定向到 Github 授权页面
        response.sendRedirect(request.authorize(null));
    }


    /**
     * Github 回调接口（步骤2）
     * <p>
     * 接收 Github 返回的授权码，获取用户信息。新用户自动创建账号和用户中心记录，
     * 已注册用户直接登录。登录成功后生成 Sa-Token 并重定向前端 oauth-success 页面。
     * 限流：每个 IP + 全局维度约 5 次/分钟。
     * </p>
     *
     * @param callback Github 回调参数（包含授权码 code 和 state）
     * @param response HTTP 响应对象，用于重定向
     * @throws IOException 重定向时可能抛出的 IO 异常
     */
    @GetMapping("/callback")
    @RateLimit(
            dimensions = {RateLimit.Dimension.IP, RateLimit.Dimension.GLOBAL}, // IP+全局维度
            permitsPerSecond = 0.083, // 约5次/分钟（5/60≈0.083）
            timeout = 0, // 无等待，直接拒绝
            fallback = "callbackFallback" // 降级方法
    )
    public void callback(AuthCallback callback,
                         HttpServletResponse response) throws IOException {
        AuthRequest request = resolveRequest();
        if (request == null) {
            redirectNotConfigured(response);
            return;
        }

        // 使用授权码从 Github 获取用户信息
        AuthResponse<AuthUser> authResponse = request.login(callback);
        AuthUser authUser = authResponse.getData();

        if (authUser == null) {
            log.warn("GitHub授权登录失败");
            // 授权失败，跳回登录页
            response.sendRedirect(frontendBaseUrl + "/login");
            return;
        }

        // 提取 Github 用户信息
        String githubId = authUser.getUuid();
        String username = authUser.getUsername();
        String avatar = authUser.getAvatar();
        User user = userMapperService.findByGithubId(githubId);

        if (user == null) {
            // 新用户：自动注册账号
            user = new User();
            user.setUsername(username);
            user.setAvatar(avatar);
            user.setGithubId(githubId);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            user.setPassword("{GITHUB_LOGIN}"); // 占位密码，Github 登录不校验密码
            user.setStatus(1);
            user.setEmail(username + "@github.com");
            userMapperService.save(user);

            // 为新用户创建用户中心记录（默认 10MB 空间）
            // 注意：此处尚未登录，取不到当前登录用户，必须用刚插入的主键回填
            UserCenter userCenter = new UserCenter();
            userCenter.setUsername(username);
            userCenter.setUserId(user.getId());
            userCenter.setCreateTime(LocalDateTime.now());
            userCenter.setUsedSpace(0L);
            userCenter.setTotalSpace(DEFAULT_TOTAL_SPACE);
            userCenterMapperService.save(userCenter);
        } else if (avatar != null && !avatar.equals(user.getAvatar())) {
            // 老用户：同步 Github 侧可能变更的头像
            user.setAvatar(avatar);
            user.setUpdateTime(LocalDateTime.now());
            userMapperService.updateById(user);
        }

        // 生成 Sa-Token 并登录（与网关共享同一份 Redis 会话）
        StpUtil.login(user.getId());
        String tokenValue = StpUtil.getTokenValue();
        log.info("GitHub登录完成: userId={}", user.getId());

        // 重定向前端 oauth-success 页面，携带 token
        response.sendRedirect(frontendBaseUrl + "/oauth-success?token=" + tokenValue);
    }

    /**
     * callback 接口的降级方法
     * <p>
     * 当回调接口触发限流时，重定向至登录页并携带错误提示信息。
     * </p>
     *
     * @param callback Github 回调参数
     * @param response HTTP 响应对象
     * @throws IOException 重定向时可能抛出的 IO 异常
     */
    public void callbackFallback(AuthCallback callback, HttpServletResponse response) throws IOException {
        // 降级逻辑：跳回登录页，并携带错误提示
        response.sendRedirect(frontendBaseUrl + "/login?error=" + encode("请求太频繁，请1分钟后再尝试"));
    }

    /**
     * 获取授权请求对象，凭据缺失时返回 {@code null}。
     *
     * @return 已配置时返回单例授权请求对象，否则返回 {@code null}
     */
    private AuthRequest resolveRequest() {
        if (!githubAuthConfig.isConfigured()) {
            log.warn("GitHub 登录未配置（oauth.github.client-id/client-secret/redirect-uri 缺失），接口不可用");
            return null;
        }
        if (githubAuthRequest == null) {
            synchronized (this) {
                if (githubAuthRequest == null) {
                    githubAuthRequest = githubAuthConfig.buildRequest();
                }
            }
        }
        return githubAuthRequest;
    }

    /**
     * 未配置凭据时的跳转，避免把 500 抛给前端。
     *
     * @param response HTTP 响应对象
     * @throws IOException 重定向时可能抛出的 IO 异常
     */
    private void redirectNotConfigured(HttpServletResponse response) throws IOException {
        response.sendRedirect(frontendBaseUrl + "/login?error=" + encode("GitHub 登录未配置"));
    }

    /**
     * URL 编码，避免中文错误提示破坏重定向地址。
     *
     * @param text 原始文本
     * @return 编码后的文本
     */
    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

}
