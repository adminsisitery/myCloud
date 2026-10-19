package com.cloudrive.fileAsyncService.ws;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebSocket 用量实时推送服务（异步服务版）
 * <p>前端经网关连接 ws://host/ws/message?token=xxx，握手阶段通过 Sa-Token 校验身份
 * （token 由 user-service 签发，存在共享 Redis 中，因此本服务可直接校验）。
 * 后端通过 {@link #sendMsg(Long, String)} 向指定用户推送消息。</p>
 */
@Slf4j
@Component
@ServerEndpoint(value = "/ws/message", configurator = WsService.SaTokenConfigurator.class)
public class WsService {

    /** userId → Session 映射，用于服务端主动推送 */
    private static final ConcurrentMap<Long, Session> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        Long userId = (Long) config.getUserProperties().get("userId");
        if (userId == null) {
            log.warn("WebSocket 握手缺少 userId，关闭连接");
            tryClose(session);
            return;
        }
        session.getUserProperties().put("userId", userId);
        SESSIONS.put(userId, session);
        log.info("WebSocket 连接建立 - userId={}, sessionId={}", userId, session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        Long userId = (Long) session.getUserProperties().get("userId");
        if (userId != null) {
            SESSIONS.remove(userId);
            log.info("WebSocket 连接断开 - userId={}, reason={}", userId, reason.getReasonPhrase());
        }
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log.error("WebSocket 异常 - sessionId={}", session == null ? null : session.getId(), e);
    }

    /**
     * 向指定用户推送消息（服务端主动调用）
     *
     * @param userId 目标用户ID
     * @param msg    消息内容
     */
    public void sendMsg(Long userId, String msg) {
        Session session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(msg);
        }
    }

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

    private void tryClose(Session session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("WebSocket关闭失败: sessionId={}", session.getId(), e);
        }
    }
}
