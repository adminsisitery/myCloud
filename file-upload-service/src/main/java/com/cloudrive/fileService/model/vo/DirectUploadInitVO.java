package com.cloudrive.fileService.model.vo;

import java.util.Map;

public record DirectUploadInitVO(
        String taskId,
        String accessKeyId,
        String accessKeySecret,
        String securityToken,
        String expiration,
        String endpoint,
        String bucket,
        String objectKey,
        int maxParallelParts,
        Map<String, String> requiredHeaders) {
}
