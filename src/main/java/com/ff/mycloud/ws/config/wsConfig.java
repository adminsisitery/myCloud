package com.ff.mycloud.ws.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 * <p>注册 ServerEndpointExporter 使 @ServerEndpoint 注解生效。
 * 身份校验由 WsService.SaTokenConfigurator 在握手阶段完成。</p>
 *
 * @author 飞飞
 * @date 2026/8/5
 */
@Configuration
public class wsConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
