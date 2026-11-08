package com.cloudrive.gateway.Filter;

import com.cloudrive.gateway.config.RateLimitConfig;
import com.cloudrive.gateway.config.RateLimitProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 登录/注册类接口的入口加固限流：按客户端 IP 做固定窗口计数，默认 5 次/分钟。
 * <p>
 * 与全局令牌桶（RequestRateLimiter）叠加生效：全局桶负责单用户总速率，本过滤器只针对
 * 撞库、短信轰炸这类入口攻击把粒度收紧到 IP + 分钟级（令牌桶只支持整数「每秒」速率，
 * 表达不了 5 次/分钟）。
 */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * INCR 与 EXPIRE 必须原子执行，否则首次计数后过期时间没设上，key 会永久留存，该 IP 将一直被拒。
     */
    private static final RedisScript<Long> COUNTER_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private static final String KEY_PREFIX = "rate:sensitive:ip:";

    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final byte[] DENY_BODY =
            "{\"code\":429,\"message\":\"操作过于频繁，请稍后再试\",\"data\":null}".getBytes(StandardCharsets.UTF_8);

    private final ReactiveStringRedisTemplate redisTemplate;

    private final RateLimitProperties rateLimitProperties;

    public LoginRateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                                RateLimitProperties rateLimitProperties) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RateLimitProperties.Login login = rateLimitProperties.getLogin();
        if (!login.isEnabled() || login.getPermits() <= 0) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!isSensitivePath(path, login.getPaths())) {
            return chain.filter(exchange);
        }

        String key = KEY_PREFIX + RateLimitConfig.clientIp(exchange);
        return redisTemplate
                .execute(COUNTER_SCRIPT, List.of(key), List.of(String.valueOf(login.getWindowSeconds())))
                .next()
                .defaultIfEmpty(0L)
                .flatMap(count -> count > login.getPermits()
                        ? deny(exchange, login.getWindowSeconds())
                        : chain.filter(exchange));
    }

    private boolean isSensitivePath(String path, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> deny(ServerWebExchange exchange, int windowSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(RETRY_AFTER_HEADER, String.valueOf(windowSeconds));
        DataBuffer buffer = response.bufferFactory().wrap(DENY_BODY);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 排在鉴权之前：这些接口本来就在免鉴权名单内，尽早拒绝可以省掉一次下游调用
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
