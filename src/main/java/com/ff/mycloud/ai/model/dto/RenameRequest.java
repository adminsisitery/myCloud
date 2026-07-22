package com.ff.mycloud.ai.model.dto;

import lombok.Data;

@Data
public class RenameRequest {
    Long conversationId;
    String name;
}
