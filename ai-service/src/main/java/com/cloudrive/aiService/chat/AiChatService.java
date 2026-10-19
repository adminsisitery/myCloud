package com.cloudrive.aiService.chat;

import com.cloudrive.aiService.chat.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiChatService {
    SseEmitter sendMessage(ChatRequest message);

    /** Agent Plan-and-Execute 模式 */
    SseEmitter sendAgentMessage(ChatRequest request);
}
