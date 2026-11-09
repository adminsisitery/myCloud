package com.cloudrive.pan_common.internal;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册全局 Feign 鉴权拦截器。
 * <p>
 * {@code @ConditionalOnClass} 保证没有引入 OpenFeign 的服务（如 user-service）
 * 不会被这段配置拖累——pan-common 里 feign-core 是 optional 依赖。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RequestInterceptor.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthFeignConfig {

    /**
     * 全局 RequestInterceptor：spring-cloud-openfeign 会自动把它挂到每个 FeignClient 上。
     */
    @Bean
    public RequestInterceptor internalAuthFeignInterceptor(InternalAuthProperties properties) {
        return new InternalAuthFeignInterceptor(properties);
    }
}
