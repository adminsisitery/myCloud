package com.cloudrive.fileService.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DirectUploadInitDTO {
    @NotBlank @Size(max = 255)
    private String fileName;
    @NotNull @Positive
    private Long fileSize;
    @Size(max = 128)
    private String fileType;
    private Long parentId = 0L;
    @NotBlank @Size(min = 64, max = 64)
    private String sha256Hash;
}
