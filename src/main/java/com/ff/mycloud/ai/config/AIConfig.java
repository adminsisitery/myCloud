package com.ff.mycloud.ai.config;

import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.service.ChatAssistant;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private final AIProperties properties;
    private final DbChatMemoryStore chatMemoryStore;

    public AIConfig(AIProperties properties, DbChatMemoryStore chatMemoryStore) {
        this.properties = properties;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Bean
    OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .build();
    }

    @Bean
    OpenAiStreamingChatModel openAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .timeout(properties.getTimeout())
                .build();
    }

    @Bean
    ChatAssistant chatAssistant(OpenAiStreamingChatModel streamingModel) {
        return AiServices.builder(ChatAssistant.class)
                .streamingChatLanguageModel(streamingModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(properties.getMaxHistory())
                        .chatMemoryStore(chatMemoryStore)
                        .id(memoryId)
                        .build())
                .build();
    }
}
