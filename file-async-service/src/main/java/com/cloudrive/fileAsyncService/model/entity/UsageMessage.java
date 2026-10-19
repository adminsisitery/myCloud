package com.cloudrive.fileAsyncService.model.entity;

import lombok.Data;

/**
 * 用量通知消息体（与单体 UsageMessage 字段一致，MQ 契约不变）
 */
@Data
public class UsageMessage {
    private Long userId;
    private Long usageSize;
    private Long totalSize;
    private String message;
}
