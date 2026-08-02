package com.ff.mycloud.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
@Data
public class AIProperties {
    /** Chat API Key（兼容旧配置，多模型时用 models 列表） */
    private String apiKey;
    /** Chat API 地址（兼容旧配置） */
    private String baseUrl = "https://api.deepseek.com";
    /** Chat 模型名（兼容旧配置） */
    private String model = "deepseek-chat";

    /** 默认模型名 */
    private String defaultModel;

    /** 多模型列表 */
    private List<ModelConfig> models = new ArrayList<>();

    /** Embedding API 地址（可与 chat 不同） */
    private String embeddingBaseUrl;
    /** Embedding API Key（默认复用 chat 的 key） */
    private String embeddingApiKey;
    /** Embedding 模型名 */
    private String embeddingModel = "text-embedding-v4";
    /** Embedding 向量维度 */
    private Integer embeddingDimensions = 1536;
    private int maxHistory = 20;
    private Duration timeout = Duration.ofSeconds(60);

    /** Tavily 联网搜索 API Key */
    private String tavilyApiKey;

    /** Prompt 模板配置 */
    private Prompts prompts = new Prompts();

    @Data
    public static class Prompts {
        /** 普通聊天模式 System Prompt */
        private String chat;
        /** Agent 模式 System Prompt */
        private String agent;
        /** Agent 规划阶段 Prompt（需包含 %s 占位符，用于插入用户请求） */
        private String agentPlanning;

        /** 转为 Map，方便 PromptService 按名取用 */
        public java.util.Map<String, String> toMap() {
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            if (chat != null) map.put("chat", chat);
            if (agent != null) map.put("agent", agent);
            if (agentPlanning != null) map.put("agent-planning", agentPlanning);
            return map;
        }
    }

    @Data
    public static class ModelConfig {
        /** 模型标识名 */
        private String name;
        /** API 地址 */
        private String baseUrl;
        /** API Key */
        private String apiKey;
        /** 实际模型名（传给 API 的 model 参数） */
        private String model;
        /** 是否为默认模型 */
        private boolean isDefault;
    }
}
