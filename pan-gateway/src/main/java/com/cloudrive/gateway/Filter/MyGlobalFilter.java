package com.cloudrive.gateway.Filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.gateway.config.AuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "UserId";

    private final AuthProperties authProperties;

    public MyGlobalFilter(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (authProperties.isExcluded(path)) {
            // 白名单路径不校验登录，但绝不能把客户端自带的 UserId 头原样透下去。
            // 鉴权分支里是同名先删再写，白名单分支若直接放行，这个头就是客户端说了算：
            // RateLimitConfig.userKeyResolver 会拿它当限流键，伪造即可轮换桶绕开全局限流，
            // 或指定某个用户把对方额度打满。身份虽由 Sa-Token 判定不受影响，
            // 但"客户端能决定网关认为你是谁"这件事本身不该留口子。
            ServerWebExchange sanitized = exchange.mutate()
                    .request(request -> request.headers(headers -> headers.remove(USER_ID_HEADER)))
                    .build();
            return chain.filter(sanitized);
        }

        final Object userId;
        try {
            StpUtil.checkLogin();
            userId = StpUtil.getLoginId();
        } catch (NotLoginException exception) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.set(USER_ID_HEADER, String.valueOf(userId));
                }))
                .build();
        return chain.filter(authenticatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}