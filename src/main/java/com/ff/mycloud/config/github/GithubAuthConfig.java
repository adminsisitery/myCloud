package com.ff.mycloud.config.github;

import lombok.Data;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 * Github OAuth 认证配置类
 * <p>
 * 读取配置文件中的 oauth.github 前缀属性（clientId、clientSecret、redirectUri），
 * 并创建 Github OAuth 授权请求对象，用于第三方登录集成。
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
     * 创建 Github OAuth 授权请求对象
     * <p>
     * 使用配置文件中的 clientId、clientSecret 和 redirectUri 构建 AuthConfig，
     * 并返回 AuthGithubRequest 实例，供授权流程使用。
     * </p>
     *
     * @return Github 授权请求对象 AuthRequest
     */
    @Bean
    public AuthRequest githubAuthRequest() {
        return new AuthGithubRequest(AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .build());
    }
}
