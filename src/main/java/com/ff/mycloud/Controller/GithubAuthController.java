package com.ff.mycloud.Controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ff.mycloud.model.entity.User;
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

    public GithubAuthController(AuthRequest githubAuthRequest, UserMapperService userMapperService) {
        this.githubAuthRequest = githubAuthRequest;
        this.userMapperService = userMapperService;
    }

    // 1 跳转 GitHub
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        //跳转
        response.sendRedirect(githubAuthRequest.authorize(null));

    }


    // 2 回调
    @GetMapping("/callback")
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
        }

        //生成sa-token
        StpUtil.login(byGithubId.getId());
        String tokenValue = StpUtil.getTokenValue();

        // 重定向前端
        response.sendRedirect(
                "http://localhost:5173/oauth-success?token=" + tokenValue
        );
    }


}