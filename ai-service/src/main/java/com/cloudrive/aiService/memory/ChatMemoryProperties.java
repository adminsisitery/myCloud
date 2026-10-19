package com.cloudrive.aiService.memory;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties("ai.chat-memory")
public class ChatMemoryProperties {

    /**
     * 对话轮数
     */
    @Min(2)
    @Max(50)
    private int windowTurns = 20;

    /**
     * 超出保留的轮数
     */
    @Min(1)
    @Max(49)
    private int retainedTurns = 10;

    /**
     * 最近消息token
     */
    @Min(1000)
    @Max(100000)
    private int recentTokenBudget = 8000;

    /**
     * 对话内容token
     */
    @Min(4096)
    @Max(200000)
    private int contextTokenBudget = 24000;

    /**
     * 最大输出token
     */
    @Min(256)
    @Max(8192)
    private int maxOutputTokens = 2048;

    /**
     * 历史摘要token上限
     */
    @Min(256)
    @Max(8192)
    private int summaryOutputTokens = 1600;

    /**
     * 历史摘要最大字符数
     */
    @Min(500)
    @Max(12000)
    private int summaryMaxChars = 6000;

    /**
     * 最大调用数
     */
    @Min(1)
    @Max(10)
    private int maxRecallCalls = 3;

    /**
     * 释放秒数
     */
    @Min(60)
    @Max(1800)
    private int leaseSeconds = 300;

    /**
     * 最大会话并发数
     */
    @Min(1)
    @Max(32)
    private int maxConcurrentChats = 8;

    /**
     * 缓存存活时间
     */
    @Min(60)
    @Max(86400)
    private int cacheTtlSeconds = 1800;

    @AssertTrue(message = "retainedTurns must be smaller than windowTurns; reserve tokens for current input, summary, tools and output")
    public boolean isValidBudget() {
        return retainedTurns < windowTurns && recentTokenBudget + maxOutputTokens + 4000 < contextTokenBudget;
    }
}
