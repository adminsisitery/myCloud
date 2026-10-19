package com.cloudrive.fileManageService.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件/文件夹列表项视图对象
 * <p>
 * 文件名输出时已补全后缀（非文件夹），方便前端直接展示。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class FileListVO {

    /** 文件 ID */
    private Long id;

    /** 文件（夹）名称（文件已补全后缀） */
    private String filename;

    /** 文件原始名称 */
    private String originalFilename;

    /** 文件在云盘中的路径 */
    private String path;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（MIME） */
    private String fileType;

    /** 父目录 ID */
    private Long parentId;

    /** 是否为文件夹 */
    private Boolean isFolder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 是否已收藏 */
    private Boolean isStarred;

    /** 收藏时间 */
    private LocalDateTime starredAt;
}
