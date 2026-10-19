package com.cloudrive.fileShareService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息实体类
 * <p>
 * 映射数据库表 t_file_info，记录用户云盘中每个文件或文件夹的元信息：
 * 文件名、路径、大小、哈希值、所属用户及父目录等。
 * 本服务对 t_file_info 同时具备读写能力（管理域的核心数据表）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@TableName("t_file_info")
public class FileInfo {

    /** 主键，自增 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件（夹）名称 */
    private String filename;

    /** 文件原始名称（上传时的文件名） */
    private String originalFilename;

    /** 文件在云盘中的路径（对象存储 key） */
    private String path;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（MIME 类型） */
    private String fileType;

    /** 文件的 SHA256 哈希值，用于去重和完整性校验 */
    private String sha256Hash;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 所属用户 ID */
    private Long userId;

    /** 父目录 ID，根目录为 null 或 0 */
    private Long parentId;

    /** 是否为文件夹 */
    @TableField("isFolder")
    private Boolean isFolder;

    /** 是否已删除（逻辑删除标记） */
    @TableField("isDeleted")
    private Boolean isDeleted;

    /** 本地存储的文件路径 */
    @TableField("local_file_path")
    private String localFilePath;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 文件删除时间 */
    private LocalDateTime deletedAt;

    /** 是否已收藏（星标） */
    @TableField("isStarred")
    private Boolean isStarred;

    /** 收藏时间 */
    private LocalDateTime starredAt;

    /** 文件分类 ID，对应 t_file_category.id */
    private Integer categoryId;
}
