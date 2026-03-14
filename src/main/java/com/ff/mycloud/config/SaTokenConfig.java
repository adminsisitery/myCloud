package com.ff.mycloud.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    
    // 注册 Sa-Token 拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能 
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 登录校验 -- 拦截所有路由，并排除一些不需要登录访问的接口
            SaRouter.match("/**")
                    .notMatch(
                        // 用户相关
                        "/api/user/login", 
                        "/api/user/register",
                            "/api/user/verification-code",
                        // 分享相关
                        "/api/shares/**",
                        "/api/shares/*/verification",
                        "/api/shares/*/content",
                        "/api/auth/github/**"

                    )
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
} 