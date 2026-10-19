package com.cloudrive.fileAsyncService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 * <p>注册 ServerEndpointExporter 使 @ServerEndpoint 注解生效；
 * 身份校验由 {@link com.cloudrive.fileAsyncService.ws.WsService.SaTokenConfigurator} 在握手阶段完成。</p>
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
