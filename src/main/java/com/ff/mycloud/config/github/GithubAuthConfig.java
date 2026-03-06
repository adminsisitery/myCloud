package com.ff.mycloud.config.github;

import lombok.Data;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/4 21:41
 * @version 1.0
 */

@Data
@Configuration
@ConfigurationProperties(prefix = "oauth.github")

public class GithubAuthConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;

    @Bean
    public AuthRequest githubAuthRequest() {
        return new AuthGithubRequest(AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .build());
    }
}
