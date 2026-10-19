package com.cloudrive.fileService.model.entity;

import lombok.Data;

/**
 * 分片上传实体类
 */
@Data
public class ChunkFile {
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Long parentId;
    private Integer totalParts;
    private Integer chunkSize;
    private String sha256Hash;
    private String filePath;
}
