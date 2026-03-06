package com.ff.mycloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}