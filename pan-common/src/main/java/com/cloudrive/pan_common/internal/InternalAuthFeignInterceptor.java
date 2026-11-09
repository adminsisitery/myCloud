package com.cloudrive.pan_common.internal;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign 请求拦截器：给所有出站调用补上服务间鉴权头。
 * <p>
 * 注册成全局 {@link RequestInterceptor} bean 后，spring-cloud-openfeign 会把它
 * 应用到每一个 {@code @FeignClient}，各服务不需要逐个客户端改代码——
 * 这正是「5 处调用方」不用逐个动的原因。
 * </p>
 * <p>
 * 为什么不只给 {@code /internal/**} 加头：{@code @FeignClient(path = "/internal/space")}
 * 的前缀是否已拼进 {@link RequestTemplate#path()} 取决于 Contract 实现，
 * 判断失误的后果是「静默不加头 → 下游 401 → 全站空间配额操作失败」，
 * 比多带一个头严重得多。本系统所有 Feign 目标都是自家服务，统一携带即可。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
public class InternalAuthFeignInterceptor implements RequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(InternalAuthFeignInterceptor.class);

    private final InternalAuthProperties properties;

    public InternalAuthFeignInterceptor(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!properties.isConfigured()) {
            // 不加头，让服务端以 401 明确报错，比伪造空 token 更容易定位
            logger.warn("pan.internal.token 未配置，调用 {} 将因缺少鉴权头被拒绝", template.path());
            return;
        }
        template.header(InternalAuthHeaders.TOKEN, properties.getToken());
        template.header(InternalAuthHeaders.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
    }
}
