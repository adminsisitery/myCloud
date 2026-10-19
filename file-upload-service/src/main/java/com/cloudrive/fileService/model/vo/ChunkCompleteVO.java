package com.cloudrive.fileService.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkCompleteVO {
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private String path;
    private String suffix;
}
