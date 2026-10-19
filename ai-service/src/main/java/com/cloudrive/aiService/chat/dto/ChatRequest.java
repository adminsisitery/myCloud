package com.cloudrive.aiService.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {
    @Positive
    Long conversationId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 8000, message = "消息不能超过8000字符")
    String message;

    /** 指定模型名，为空则用默认模型 */
    @Size(max = 100)
    String model;

    /** 显式允许本轮向外部搜索服务发送查询。 */
    boolean webSearchEnabled;

    /** 同一次提交重试时保持不变，避免重复调用模型。 */
    @jakarta.validation.constraints.Pattern(regexp="[a-zA-Z0-9_-]{8,80}")
    String requestId;
}
