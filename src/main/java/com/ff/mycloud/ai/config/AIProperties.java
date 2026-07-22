package com.ff.mycloud.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
@Data
public class AIProperties {
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
    private int maxHistory = 20; // 上下文窗口消息数
    private Duration timeout = Duration.ofSeconds(60);
}

