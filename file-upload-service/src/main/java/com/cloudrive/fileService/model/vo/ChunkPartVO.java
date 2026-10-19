package com.cloudrive.fileService.model.vo;

import lombok.Data;

@Data
public class ChunkPartVO {
    private String uploadId;
    private int partNumber;
    private int progress;
    private Boolean completed;
}
