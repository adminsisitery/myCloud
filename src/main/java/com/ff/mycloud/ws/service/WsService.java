package com.ff.mycloud.ws.service;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebSocket 用量实时推送服务
 * <p>前端连接 ws://host/ws/message?token=xxx，握手阶段通过 Sa-Token 校验身份。
 * 后端通过 {@link #sendMsg(Long, String)} 向指定用户推送消息。</p>
 *
 * @author 飞飞
 * @date 2026/8/5
 */
@Component
@ServerEndpoint(value = "/ws/message", configurator = WsService.SaTokenConfigurator.class)
public class WsService {

    private static final Logger log = LoggerFactory.getLogger(WsService.class);

    /** userId → Session 映射，用于服务端主动推送 */
    private static final ConcurrentMap<Long, Session> sessions = new ConcurrentHashMap<>();

    // ==================== WebSocket 生命周期回调 ====================

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        Long userId = (Long) config.getUserProperties().get("userId");
        if (userId == null) {
            log.warn("WebSocket 握手缺少 userId，关闭连接");
            tryClose(session);
            return;
        }
        session.getUserProperties().put("userId", userId);
        sessions.put(userId, session);
        log.info("WebSocket 连接建立 - userId={}, sessionId={}", userId, session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        Long userId = (Long) session.getUserProperties().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket 连接断开 - userId={}, reason={}", userId, reason.getReasonPhrase());
        }
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log.error("WebSocket 异常 - sessionId={}", session == null ? null : session.getId(), e);
    }

    // ==================== 发送消息 ====================

    /**
     * 向指定用户推送消息（服务端主动调用）
     *
     * @param userId 目标用户ID
     * @param msg    消息内容
     */
    public void sendMsg(Long userId, String msg) {
        Session session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(msg);
        }
    }

    // ==================== 握手拦截器 ====================

    /**
     * WebSocket 握手配置器，从请求参数中提取 Sa-Token 并校验身份。
     * 前端连接时需携带 token 参数：ws://host/ws/message?token=xxx
     */
    public static class SaTokenConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig config,
                                     HandshakeRequest request,
                                     HandshakeResponse response) {
            String token = getQueryParam(request, "token");
            if (token != null && !token.isEmpty()) {
                try {
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        config.getUserProperties().put("userId", Long.valueOf(loginId.toString()));
                    }
                } catch (Exception e) {
                    log.warn("WebSocket 握手 token 校验失败: {}", e.getMessage());
                }
            }
        }

        private String getQueryParam(HandshakeRequest request, String name) {
            String query = request.getQueryString();
            if (query == null) {
                return null;
            }
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
            return null;
        }
    }

    // ==================== 内部工具 ====================

    private void tryClose(Session session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("WebSocket关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
