package com.ff.mycloud.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP工具类，用于获取当前HTTP请求的客户端IP地址。
 * 优先从代理头获取，最后回退到直连IP。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class IpUtil {

    private static final String UNKNOWN = "unknown";

    /**
     * 获取当前请求的客户端 IP
     * 优先取代理头，最后取直接 IP
     */
    public static String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return UNKNOWN;
        }
        HttpServletRequest req = attrs.getRequest();

        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }
}
