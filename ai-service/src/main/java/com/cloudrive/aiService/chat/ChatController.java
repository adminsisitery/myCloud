package com.cloudrive.aiService.chat;

import com.cloudrive.aiService.chat.dto.ChatRequest;
import com.cloudrive.aiService.model.ModelRegistry;
import com.cloudrive.pan_common.annotation.RateLimit;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final AiChatService aiChatService;
    private final ModelRegistry modelRegistry;

    public ChatController(AiChatService aiChatService, ModelRegistry modelRegistry) {
        this.aiChatService = aiChatService;
        this.modelRegistry = modelRegistry;
    }

    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 3.0)
    public SseEmitter chat(@RequestBody @Valid ChatRequest request) {
        return aiChatService.sendMessage(request);
    }

    /** Agent Plan-and-Execute 模式 */
    @PostMapping(value = "/agent", produces = "text/event-stream;charset=UTF-8")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 1.0)
    public SseEmitter agentChat(@RequestBody @Valid ChatRequest request) {
        return aiChatService.sendAgentMessage(request);
    }

    /** 获取可用模型列表（给前端下拉框） */
    @GetMapping("/models")
    public Map<String, Object> models() {
        List<String> list = modelRegistry.availableModels();
        return Map.of(
                "code", 200,
                "data", Map.of(
                        "models", list,
                        "defaultModel", modelRegistry.getDefaultModelName()
                )
        );
    }
}
