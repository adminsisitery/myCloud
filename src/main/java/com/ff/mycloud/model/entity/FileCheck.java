package com.ff.mycloud.model.entity;

import lombok.Data;

/**
 * 文件校验信息类
 * <p>用于秒传功能的前置校验，封装待上传文件的基本信息，
 * 服务端通过比对文件哈希值判断是否可以跳过实际上传（秒传）。
 *
 * @author 飞飞
 * @date 2026/3/13 18:20
 * @version 1.0
 */
@Data
public class FileCheck {
    /** 文件名 */
    private String fileName;
    /** 文件类型（MIME类型） */
    private String fileType;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 父目录ID */
    private String parentId;
    /** 文件后缀名 */
    private String fileSuffix;
    /** 文件的SHA256哈希值，用于秒传去重校验 */
    private String sha256Hash;
}
