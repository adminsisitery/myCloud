package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 微服务启动类
 * <p>
 * 承载 AI 对话、Agent 任务、RAG 检索、记忆持久化与用量计费，
 * 原单体 {@code com.ff.mycloud.ai} 包整体迁入本模块。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan({"com.cloudrive.aiService.persistence.mapper", "com.cloudrive.aiService.repositorys"})
@EnableFeignClients(basePackages = "com.cloudrive.aiService.client")
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
