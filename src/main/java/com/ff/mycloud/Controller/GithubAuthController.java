package com.ff.mycloud.Controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.service.UserCenterMapperService;
import com.ff.mycloud.service.UserMapperService;
import com.ff.mycloud.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import me.zhyd.oauth.model.AuthCallback;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Github OAuth 认证控制器
 * <p>
 * 处理 Github 第三方登录的完整流程：跳转 Github 授权页面 -> Github 回调 ->
 * 获取用户信息 -> 新用户自动注册/老用户登录 -> 生成 Sa-Token 并重定向至前端。
 * 包含限流保护，登录和回调接口均限制为约 5 次/分钟。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/4 21:50
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth/github")
public class GithubAuthController {

    private final AuthRequest githubAuthRequest;
    private final UserMapperService userMapperService;
    UserCenterMapperService userCenterMapperService;

    /**
     * 构造器注入 Github 授权请求、用户服务和用户中心服务
     *
     * @param githubAuthRequest        Github OAuth 授权请求对象
     * @param userMapperService        用户 Mapper 服务
     * @param userCenterMapperService  用户中心 Mapper 服务
     */
    public GithubAuthController(AuthRequest githubAuthRequest, UserMapperService userMapperService, UserCenterMapperService userCenterMapperService) {
        this.githubAuthRequest = githubAuthRequest;
        this.userMapperService = userMapperService;
        this.userCenterMapperService = userCenterMapperService;

    }

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
        // 重定向到 Github 授权页面
        response.sendRedirect(githubAuthRequest.authorize(null));

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
        // 使用授权码从 Github 获取用户信息
        AuthResponse<AuthUser> authResponse = githubAuthRequest.login(callback);
        AuthUser authUser = authResponse.getData();

        if (authUser == null) {
            // 授权失败，跳回登录页
            response.sendRedirect("http://localhost:5173/login");
            return;
        }

        // 提取 Github 用户信息
        String accessToken = authUser.getToken().getAccessToken();
        String githubId = authUser.getUuid();
        String username = authUser.getUsername();
        String avatar = authUser.getAvatar();
        User byGithubId = userMapperService.findByGithubId(githubId);


        if (byGithubId==null ){
            // 新用户：自动注册账号
            byGithubId = new User();
            byGithubId.setUsername(username);
            byGithubId.setAvatar(avatar);
            byGithubId.setGithubId(githubId);
            byGithubId.setCreateTime(LocalDateTime.now());
            byGithubId.setUpdateTime(LocalDateTime.now());
            byGithubId.setPassword("{GITHUB_LOGIN}"); // 占位密码，Github 登录不校验密码
            byGithubId.setStatus(1);
            byGithubId.setEmail(username+"@github.com");
            userMapperService.save(byGithubId);

            // 为新用户创建用户中心记录（默认 10MB 空间）
            UserCenter userCenter = new UserCenter();
            userCenter.setUsername(username);
            userCenter.setUserId(UserContext.getCurrentUserId());
            userCenter.setCreateTime(LocalDateTime.now());
            userCenter.setUsedSpace(0L);
            userCenter.setTotalSpace(10485760);
            userCenterMapperService.save(userCenter);
        }

        // 生成 Sa-Token 并登录
        StpUtil.login(byGithubId.getId());
        String tokenValue = StpUtil.getTokenValue();

        // 重定向前端 oauth-success 页面，携带 token
        response.sendRedirect(
                "http://localhost:5173/oauth-success?token=" + tokenValue
        );

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
        response.sendRedirect("http://localhost:5173/login?error=请求太频繁，请1分钟后再尝试");
    }

}