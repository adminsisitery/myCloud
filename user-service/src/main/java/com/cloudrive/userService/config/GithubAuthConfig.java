package com.cloudrive.userService.config;

import lombok.Data;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Github OAuth 认证配置类
 * <p>
 * 读取配置文件中的 oauth.github 前缀属性（clientId、clientSecret、redirectUri）。
 * 注意：这里故意不在启动时创建 {@link AuthRequest}。JustAuth 的请求对象在构造时就会校验
 * 凭据是否完整，凭据缺失会直接抛异常导致整个服务启动失败——GitHub 登录只是可选能力，
 * 不应该拖垮账号密码登录主链路。因此改为首次使用时才构建。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/4 21:41
 * @version 1.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "oauth.github")
public class GithubAuthConfig {

    /** Github OAuth 客户端ID */
    private String clientId;
    /** Github OAuth 客户端密钥 */
    private String clientSecret;
    /** Github OAuth 回调地址 */
    private String redirectUri;

    /**
     * 判断 GitHub 登录是否已配置完整。
     *
     * @return 三项凭据齐全时返回 {@code true}
     */
    public boolean isConfigured() {
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(redirectUri);
    }

    /**
     * 创建 Github OAuth 授权请求对象。
     * <p>调用前应先用 {@link #isConfigured()} 判断，凭据缺失时 JustAuth 会抛异常。</p>
     *
     * @return Github 授权请求对象 AuthRequest
     */
    public AuthRequest buildRequest() {
        return new AuthGithubRequest(AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .build());
    }
}
