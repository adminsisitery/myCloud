package com.cloudrive.pan_common.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 给 {@code /internal/**} 挂上服务间鉴权拦截器。
 * <p>
 * 放在 pan-common 里，是因为 12 个服务都从 {@code com.cloudrive} 根包扫描，
 * 这份配置会自动进入每个 Servlet 服务的容器——新增服务不需要再记得加一遍，
 * 漏加就等于漏保护，这类事不该靠人记。
 * </p>
 * <p>
 * 网关是 WebFlux 应用且不依赖 pan-common，不会受此影响。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthWebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(InternalAuthWebConfig.class);

    private final InternalAuthProperties properties;

    public InternalAuthWebConfig(InternalAuthProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            logger.warn("pan.internal.token 未配置：所有 /internal/** 请求都会被拒绝。"
                    + "请通过环境变量 INTERNAL_TOKEN 或共享配置下发同一份密钥。");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(properties))
                .addPathPatterns("/internal/**")
                // 先于业务拦截器执行，避免未授权的请求先跑进业务逻辑
                .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
