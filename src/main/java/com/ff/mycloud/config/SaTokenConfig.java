package com.ff.mycloud.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 权限认证配置类
 * <p>
 * 配置 Sa-Token 拦截器，对全局请求进行登录校验。
 * 排除登录、注册、验证码、分享相关以及 Github OAuth 回调等无需登录即可访问的接口。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器
     * <p>
     * 拦截所有路径（/**），但排除指定的公开接口（登录、注册、验证码、分享访问、Github OAuth）。
     * 未登录用户访问受保护接口时，将触发 StpUtil.checkLogin() 进行登录校验。
     * </p>
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor(handler -> {
            try {
                // 登录校验 -- 拦截所有路由，并排除一些不需要登录访问的接口
                SaRouter.match("/**")
                        .notMatch(
                            // 用户相关：登录、注册、获取验证码
                            "/api/user/login",
                            "/api/user/register",
                            "/api/user/verification-code",
                            // 分享相关：查看分享、验证分享密码、下载分享内容
                            "/api/shares/**",
                            "/api/shares/*/verification",
                            "/api/shares/*/content",
                            // Github OAuth 授权相关
                            "/api/auth/github/**",
                            // Swagger 文档
                            "/swagger-ui/**",
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/swagger-resources/**",
                            // Tomcat 内部错误页面
                            "/error"
                        )
                        .check(r -> StpUtil.checkLogin());
            } catch (cn.dev33.satoken.exception.SaTokenContextException e) {
                // Tomcat 异步 dispatch（如 /error）时 SaToken 上下文未初始化，放行
            }
        })).addPathPatterns("/**");
    }
}