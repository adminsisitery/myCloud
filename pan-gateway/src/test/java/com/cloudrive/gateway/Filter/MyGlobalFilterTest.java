package com.cloudrive.gateway.Filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.gateway.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MyGlobalFilter 单元测试：网关鉴权与 UserId 头注入。
 * <p>
 * 对应 TEST-CASES.md 的「MyGlobalFilter.filter」测试点。
 * 用 MockServerWebExchange 构造请求，mock 掉 GatewayFilterChain 和 StpUtil 静态方法，
 * 不启 Spring 上下文——测的是过滤器本身的三个分支：白名单放行、未登录 401、已登录注入头。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MyGlobalFilterTest {

    private static final Long LOGIN_ID = 7L;

    private AuthProperties authProperties;
    private MyGlobalFilter filter;

    @Mock GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setExcludePaths(List.of("/api/user/login", "/api/shares/**"));
        filter = new MyGlobalFilter(authProperties);
        // 共享 setUp 里的桩，401 / order 这两条用例不会走到转发，用 lenient 避免严格模式的误报
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("白名单路径 → 直接放行，不校验登录")
    void filter_白名单路径直接放行() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/user/login").build());

        // 没有 mock StpUtil：一旦过滤器去校验登录就会抛 SaTokenContextException，用例立刻红
        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("白名单通配路径 /api/shares/** → 放行")
    void filter_白名单通配路径放行() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/shares/abc123/content").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("未登录 → 返回 401，且不转发请求")
    void filter_未登录时返回401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files").build());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin)
                    .thenThrow(new NotLoginException("未登录", "login", "-1"));

            filter.filter(exchange, chain).block();
        }

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("已登录 → 转发请求，并注入 UserId 头")
    void filter_已登录时注入UserId头() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files").build());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
            stp.when(StpUtil::getLoginId).thenReturn(LOGIN_ID);

            filter.filter(exchange, chain).block();
        }

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("UserId")).isEqualTo("7");
    }

    @Test
    @DisplayName("客户端自带 UserId 头 → 被网关注入值覆盖，不能伪造身份")
    void filter_覆盖客户端伪造的UserId头() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files").header("UserId", "999").build());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
            stp.when(StpUtil::getLoginId).thenReturn(LOGIN_ID);

            filter.filter(exchange, chain).block();
        }

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        String injected = captor.getValue().getRequest().getHeaders().getFirst("UserId");
        assertThat(injected).isEqualTo("7");
        assertThat(captor.getValue().getRequest().getHeaders().get("UserId")).hasSize(1);
    }

    @Test
    @DisplayName("过滤器优先级为最高，鉴权先于路由转发")
    void getOrder_返回最高优先级() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
