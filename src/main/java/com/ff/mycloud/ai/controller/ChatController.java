package com.ff.mycloud.ai.controller;

import com.ff.mycloud.ai.model.dto.ChatRequest;
import com.ff.mycloud.ai.service.AiChatService;
import com.ff.mycloud.common.annotation.RateLimit;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 3.0)
    public SseEmitter chat(@RequestBody @Valid ChatRequest request) {
        return aiChatService.sendMessage(request);
    }
}
