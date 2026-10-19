package com.cloudrive.aiService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息实体（ai-service 只读/受限读副本）
 * <p>
 * 与 file-manage-service 的 FileInfo 字段保持一致，映射 {@code t_file_info}。
 * ai-service 在 Agent 工具与动作校验中只读取文件元数据；唯一写路径是
 * SAVE_DOCUMENT 动作（上传 AI 生成的文档），写入字段与主数据保持一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
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
