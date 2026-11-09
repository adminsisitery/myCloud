package com.cloudrive.pan_common.internal;

import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 服务间接口鉴权拦截器，作用于 {@code /internal/**}。
 * <p>
 * 背景：网关只把已认证用户的 id 写进 {@code UserId} 头，之后完全信任该头。
 * 一旦某个服务端口能被内网或公网直连，任何人加上 {@code UserId: 1} 就能冒充用户，
 * 更可以直接调 {@code /internal/space/**} 给任意用户刷配额、扣空间（水平越权）。
 * </p>
 * <p>
 * 校验三件事，任何一项不过一律 401：
 * </p>
 * <ol>
 *   <li>服务端已配置 {@code pan.internal.token}（未配置 = 全部拒绝，宁可不可用也不裸奔）；</li>
 *   <li>请求头 {@link InternalAuthHeaders#TOKEN} 与配置值一致（常量时间比较，避免时序侧信道）；</li>
 *   <li>请求头 {@link InternalAuthHeaders#TIMESTAMP} 与服务器时间偏差不超过容差（默认 60s），使抓包重放失效。</li>
 * </ol>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(InternalAuthInterceptor.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final InternalAuthProperties properties;

    public InternalAuthInterceptor(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String expected = properties.getToken();
        if (!properties.isConfigured()) {
            // 配置缺失时拒绝而不是放行：否则一次漏配就把内网接口重新变成裸奔状态。
            logger.error("pan.internal.token 未配置，已拒绝内部调用 {} {}", request.getMethod(), request.getRequestURI());
            return reject(response, "服务间鉴权未配置，内部接口不可用");
        }

        String actualToken = request.getHeader(InternalAuthHeaders.TOKEN);
        String timestamp = request.getHeader(InternalAuthHeaders.TIMESTAMP);
        if (!StringUtils.hasText(actualToken) || !StringUtils.hasText(timestamp)) {
            logger.warn("内部调用缺少鉴权头：{} {} from {}", request.getMethod(), request.getRequestURI(),
                    request.getRemoteAddr());
            return reject(response, "内部调用凭证缺失");
        }
        if (!constantTimeEquals(actualToken, expected)) {
            logger.warn("内部调用 token 不匹配：{} {} from {}", request.getMethod(), request.getRequestURI(),
                    request.getRemoteAddr());
            return reject(response, "内部调用凭证无效");
        }

        long issuedAt;
        try {
            issuedAt = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return reject(response, "内部调用时间戳格式错误");
        }
        long skew = Math.abs(System.currentTimeMillis() - issuedAt);
        if (skew > properties.getTolerance().toMillis()) {
            logger.warn("内部调用时间戳偏差 {}ms 超出容差：{} {}", skew, request.getMethod(), request.getRequestURI());
            return reject(response, "内部调用时间戳超出允许范围");
        }
        return true;
    }

    /**
     * 常量时间比较，避免通过响应耗时逐字节猜 token。
     */
    private boolean constantTimeEquals(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(CommonConstants.StatusCode.UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                Result.error(CommonConstants.StatusCode.UNAUTHORIZED, message));
        return false;
    }
}
