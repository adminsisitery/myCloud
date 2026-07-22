package com.ff.mycloud.ai.service;

import com.ff.mycloud.ai.model.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
    SseEmitter sendMessage(ChatRequest message);
}
