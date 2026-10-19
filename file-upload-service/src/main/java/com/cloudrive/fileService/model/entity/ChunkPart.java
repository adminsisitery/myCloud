package com.cloudrive.fileService.model.entity;

import lombok.Data;

import java.io.File;

@Data
public class ChunkPart {
    private String uploadId;
    private int partNumber;
    private File chunk;
}
