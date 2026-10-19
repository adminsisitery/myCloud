package com.cloudrive.pan_common.domain.po;

import lombok.Data;

@Data
public class UsageMessage {
    Long userId;
    Long usageSize;
    Long totalSize;
    String message;
}
