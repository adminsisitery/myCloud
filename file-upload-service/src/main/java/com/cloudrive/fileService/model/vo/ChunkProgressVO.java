package com.cloudrive.fileService.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChunkProgressVO {
    private String uploadId;
    private String fileName;
    private Long fileSize;
    private Integer totalParts;
    private List<Integer> completedParts;
    private List<Integer> missingParts;
    private Double progressPercent;
}
