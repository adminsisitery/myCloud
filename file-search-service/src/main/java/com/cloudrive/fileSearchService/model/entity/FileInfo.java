package com.cloudrive.fileSearchService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息实体类（只读）
 * <p>
 * 映射数据库表 t_file_info。文件搜索服务只读取该表用于 MySQL LIKE 策略与
 * ES 命中后的归属/未删除校验，不写入任何数据。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
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
