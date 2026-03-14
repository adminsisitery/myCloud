package com.ff.mycloud.model.entity;

import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/13 18:20
 * @version 1.0
 */

@Data
public class FileCheck {
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String parentId;
    private String fileSuffix;
    private String sha256Hash;
}
