package com.cloudrive.fileDownloadService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息实体类（下载服务只读副本）
 * <p>
 * 映射数据库表 t_file_info，与 file-upload-service 中的同名实体字段保持一致。
 * 下载服务只做查询校验，不写该表，因此没有迁移任何更新/删除方法。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@TableName("t_file_info")
public class FileInfo {

    /** 主键，自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件（夹）名称 */
    private String filename;

    /** 文件原始名称（上传时的文件名） */
    private String originalFilename;

    /** 文件在云盘中的路径 */
    private String path;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（MIME类型） */
    private String fileType;

    /** 文件的SHA256哈希值，用于去重和完整性校验 */
    private String sha256Hash;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 所属用户ID */
    private Long userId;

    /** 父目录ID，根目录为null或0 */
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

    /** 是否已收藏/星标 */
    @TableField("isStarred")
    private Boolean isStarred;

    /** 收藏时间 */
    private LocalDateTime starredAt;

    /** 文件分类ID，对应 t_file_category.id */
    private Integer categoryId;

}
