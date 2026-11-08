package com.cloudrive.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流键解析。
 * <p>
 * 优先按登录用户取键：{@link com.cloudrive.gateway.Filter.MyGlobalFilter} 校验通过后会把 UserId 写入请求头，
 * 同名头在写入前已被移除，客户端伪造无效。未登录请求退回按客户端 IP，避免匿名流量共用同一个桶。
 */
@Configuration
public class RateLimitConfig {

    public static final String USER_ID_HEADER = "UserId";

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
            if (StringUtils.hasText(userId)) {
                return Mono.just("rate:user:" + userId);
            }
            return Mono.just("rate:ip:" + clientIp(exchange));
        };
    }

    /**
     * 取真实客户端 IP。网关前面若还有 Nginx / SLB，转发头首段才是原始客户端地址。
     */
    public static String clientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String forwardedFor = headers.getFirst(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = headers.getFirst(X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return UNKNOWN;
    }
}
