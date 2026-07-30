package com.ff.mycloud.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    Long conversationId;

    @NotBlank(message = "消息不能为空")
    String message;

    /** 指定模型名，为空则用默认模型 */
    String model;
}
