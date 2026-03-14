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
 * @description: TODO
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

    public GithubAuthController(AuthRequest githubAuthRequest, UserMapperService userMapperService, UserCenterMapperService userCenterMapperService) {
        this.githubAuthRequest = githubAuthRequest;
        this.userMapperService = userMapperService;
        this.userCenterMapperService = userCenterMapperService;

    }

    // 1 跳转 GitHub
    @GetMapping("/login")
    @RateLimit(
            dimensions = {RateLimit.Dimension.IP, RateLimit.Dimension.GLOBAL}, // IP+全局维度
            permitsPerSecond = 0.083, // 约5次/分钟（5/60≈0.083）
            timeout = 0, // 无等待，直接拒绝
            fallback = "callbackFallback" // 降级方法
    )
    public void login(HttpServletResponse response) throws IOException {
        //跳转
        response.sendRedirect(githubAuthRequest.authorize(null));

    }


    // 2 回调
    @GetMapping("/callback")
    // 2 回调 - 加限流
    @RateLimit(
            dimensions = {RateLimit.Dimension.IP, RateLimit.Dimension.GLOBAL}, // IP+全局维度
            permitsPerSecond = 0.083, // 约5次/分钟（5/60≈0.083）
            timeout = 0, // 无等待，直接拒绝
            fallback = "callbackFallback" // 降级方法
    )
    public void callback(AuthCallback callback,
                         HttpServletResponse response) throws IOException {
        AuthResponse<AuthUser> authResponse = githubAuthRequest.login(callback);
        AuthUser authUser = authResponse.getData();

        if (authUser == null) {
            // 授权失败，跳回登录页
            response.sendRedirect("http://localhost:5173/login");
            return;
        }


        String accessToken = authUser.getToken().getAccessToken();
        String githubId = authUser.getUuid();
        String username = authUser.getUsername();
        String avatar = authUser.getAvatar();
        User byGithubId = userMapperService.findByGithubId(githubId);


        if (byGithubId==null ){
            //注册
            byGithubId = new User();
            byGithubId.setUsername(username);
            byGithubId.setAvatar(avatar);
            byGithubId.setGithubId(githubId);
            byGithubId.setCreateTime(LocalDateTime.now());
            byGithubId.setUpdateTime(LocalDateTime.now());
            byGithubId.setPassword("{GITHUB_LOGIN}"); // 只占位，永远不会用
            byGithubId.setStatus(1);
            byGithubId.setEmail(username+"@github.com");
            userMapperService.save(byGithubId);

            UserCenter userCenter = new UserCenter();
            userCenter.setUsername(username);
            userCenter.setUserId(UserContext.getCurrentUserId());
            userCenter.setCreateTime(LocalDateTime.now());
            userCenter.setUsedSpace(0L);
            userCenter.setTotalSpace(10485760);
            userCenterMapperService.save(userCenter);
        }

        //生成sa-token
        StpUtil.login(byGithubId.getId());
        String tokenValue = StpUtil.getTokenValue();

        // 重定向前端
        response.sendRedirect(
                "http://localhost:5173/oauth-success?token=" + tokenValue
        );

    }
    // callback接口的降级方法（参数/返回值和原方法一致）
    public void callbackFallback(AuthCallback callback, HttpServletResponse response) throws IOException {
        // 降级逻辑：跳回登录页，并携带错误提示
        response.sendRedirect("http://localhost:5173/login?error=请求太频繁，请1分钟后再尝试");
    }

}