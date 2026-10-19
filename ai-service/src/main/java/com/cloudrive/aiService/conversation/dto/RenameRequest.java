package com.cloudrive.aiService.conversation.dto;

import lombok.Data;

@Data
public class RenameRequest {
    Long conversationId;
    String name;
}
