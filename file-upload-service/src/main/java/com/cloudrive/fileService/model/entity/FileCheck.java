package com.cloudrive.fileService.model.entity;

import lombok.Data;

/**
 * 文件秒传判定参数
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.model.entity.FileCheck}。
 * 用于秒传判定的前置校验：客户端把待上传文件的 SHA-256、大小等信息传给
 * {@code POST /api/files/check}，服务端据此判断云端是否已有可复用的物理对象。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class FileCheck {

    /** 文件名 */
    private String fileName;

    /** 文件类型（MIME 类型） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 父目录ID */
    private String parentId;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 文件的 SHA-256 哈希值，用于秒传去重校验 */
    private String sha256Hash;
}
