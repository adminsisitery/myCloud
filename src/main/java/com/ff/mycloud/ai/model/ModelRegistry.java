package com.ff.mycloud.ai.model;

import com.ff.mycloud.ai.config.AIProperties;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 管理所有已注册的 AI 模型实例（单例，线程安全）。
 * <p>
 * 每个模型配置对应一对 ChatModel + StreamingChatModel。
 * 支持运行时可用的模型列表查询和按名称路由。
 */
@Component
public class ModelRegistry {

    private final Map<String, OpenAiChatModel> chatModels = new LinkedHashMap<>();
    private final Map<String, OpenAiStreamingChatModel> streamingModels = new LinkedHashMap<>();
    private final String defaultModelName;

    public ModelRegistry(AIProperties properties, List<ChatModelListener> listeners) {
        // 多模型模式
        if (properties.getModels() != null && !properties.getModels().isEmpty()) {
            for (AIProperties.ModelConfig cfg : properties.getModels()) {
                register(cfg, listeners);
            }
            this.defaultModelName = properties.getDefaultModel() != null
                    ? properties.getDefaultModel()
                    : properties.getModels().get(0).getName();
        } else {
            // 兼容旧单模型配置
            String name = properties.getModel() != null ? properties.getModel() : "deepseek-chat";
            this.defaultModelName = name;
            chatModels.put(name, OpenAiChatModel.builder()
                    .apiKey(properties.getApiKey())
                    .baseUrl(properties.getBaseUrl())
                    .modelName(name)
                    .timeout(properties.getTimeout())
                    .maxRetries(0)
                    .listeners(listeners)
                    .build());
            streamingModels.put(name, OpenAiStreamingChatModel.builder()
                    .apiKey(properties.getApiKey())
                    .baseUrl(properties.getBaseUrl())
                    .modelName(name)
                    .timeout(properties.getTimeout())
                    .listeners(listeners)
                    .build());
        }
        if (!chatModels.containsKey(defaultModelName)) throw new IllegalArgumentException("ai.default-model is not registered");
    }

    private void register(AIProperties.ModelConfig cfg, List<ChatModelListener> listeners) {
        if (cfg.getName() == null || cfg.getName().isBlank() || chatModels.keySet().stream().anyMatch(n -> n.equalsIgnoreCase(cfg.getName())))
            throw new IllegalArgumentException("AI model names must be nonblank and unique");
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(cfg.getApiKey())
                .baseUrl(cfg.getBaseUrl())
                .modelName(cfg.getModel() != null ? cfg.getModel() : cfg.getName())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(0)
                .listeners(listeners)
                .build();

        OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(cfg.getApiKey())
                .baseUrl(cfg.getBaseUrl())
                .modelName(cfg.getModel() != null ? cfg.getModel() : cfg.getName())
                .timeout(Duration.ofSeconds(120))
                .strictTools(true)
                .build();

        chatModels.put(cfg.getName(), chatModel);
        streamingModels.put(cfg.getName(), streamingModel);
    }

    // ==================== 查询 ====================

    public OpenAiChatModel getChatModel(String name) {
        String key = resolveName(name);
        OpenAiChatModel model = chatModels.get(key);
        if (model == null && !key.equals(defaultModelName)) {
            model = chatModels.get(defaultModelName);
        }
        return model;
    }

    public OpenAiStreamingChatModel getStreamingModel(String name) {
        String key = resolveName(name);
        OpenAiStreamingChatModel model = streamingModels.get(key);
        if (model == null && !key.equals(defaultModelName)) {
            model = streamingModels.get(defaultModelName);
        }
        return model;
    }

    public String getDefaultModelName() {
        return defaultModelName;
    }

    public String requireModelName(String name) {
        String resolved = resolveName(name);
        if (!chatModels.containsKey(resolved)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "不支持的模型");
        return resolved;
    }

    /** 所有可用模型名（给前端下拉框） */
    public List<String> availableModels() {
        return List.copyOf(streamingModels.keySet());
    }

    private String resolveName(String name) {
        if (name == null || name.isBlank()) return defaultModelName;
        // 处理前端传的是实际 model 名（如 "Qwen-Flash"）而不是注册名（如 "qwen-Flash"）
        if (streamingModels.containsKey(name)) return name;
        // 不区分大小写查找
        for (String key : streamingModels.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return name;
    }
}
