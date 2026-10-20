package com.cloudrive.fileSearchService.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件/文件夹列表项视图对象
 * <p>
 * 与 file-manage-service 的 FileListVO 字段完全一致，保证前端契约不变。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
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

    private String fileSuffix;

    private Boolean isStarred;

    private LocalDateTime starredAt;
}
