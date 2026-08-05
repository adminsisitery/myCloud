package com.ff.mycloud.model.entity;

import lombok.Data;

@Data
public class UsageMessage {
    Long userId;
    Long usageSize;
    Long totalSize;
    String message;
}
