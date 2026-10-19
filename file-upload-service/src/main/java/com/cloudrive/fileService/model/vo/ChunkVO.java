package com.cloudrive.fileService.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkVO {
    String uploadId;
    String ossUploadId;
}
