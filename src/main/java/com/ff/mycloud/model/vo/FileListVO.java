package com.ff.mycloud.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件列表视图对象
 * 功能描述：用于前端展示文件或文件夹的列表信息，包含文件的基本元数据，如名称、大小、类型、路径、时间等
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class FileListVO {

    /** 文件唯一标识ID */
    private Long id;

    /** 文件在系统中的存储名称（可能经过重命名或编码） */
    private String filename;

    /** 文件的原始名称（用户上传时的文件名） */
    private String originalFilename;

    /** 文件在存储系统中的相对路径 */
    private String path;

    /** 文件大小，单位为字节 */
    private Long fileSize;

    /** 文件MIME类型或类别标识 */
    private String fileType;

    /** 父级文件夹ID，用于构建目录树结构 */
    private Long parentId;

    /** 是否为文件夹，true表示文件夹，false表示普通文件 */
    private Boolean isFolder;

    /** 文件或文件夹的创建时间 */
    private LocalDateTime createdAt;

    /** 文件或文件夹的最后更新时间 */
    private LocalDateTime updatedAt;

    /** 文件后缀名，如 .txt、.jpg、.pdf 等 */
    private String fileSuffix;
} 