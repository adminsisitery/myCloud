package com.ff.mycloud.ai.conversation.dto;

import lombok.Data;

@Data
public class RenameRequest {
    Long conversationId;
    String name;
}
