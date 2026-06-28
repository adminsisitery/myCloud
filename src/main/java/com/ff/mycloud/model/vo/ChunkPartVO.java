package com.ff.mycloud.model.vo;

import lombok.Data;

@Data
public class ChunkPartVO {
    private String uploadId;
    private int partNumber;
    private int progress;
    private Boolean completed;
}
