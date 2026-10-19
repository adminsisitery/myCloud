package com.cloudrive.fileAsyncService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息（异步服务只读使用：索引同步时校验实时数据）
 */
@Data
@TableName("t_file_info")
public class FileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String filename;

    private String originalFilename;

    private String path;

    private Long fileSize;

    private String fileType;

    private String sha256Hash;

    private String fileSuffix;

    private Long userId;

    private Long parentId;

    @TableField("isFolder")
    private Boolean isFolder;

    @TableField("isDeleted")
    private Boolean isDeleted;

    @TableField("local_file_path")
    private String localFilePath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @TableField("isStarred")
    private Boolean isStarred;

    private LocalDateTime starredAt;

    private Integer categoryId;
}
