package com.ff.mycloud.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileListVO {
    private Long id;
    private String filename;
    private String originalFilename;
    private String path;
    private Long fileSize;
    private String fileType;
    private Long parentId;
    private Boolean isFolder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
} 