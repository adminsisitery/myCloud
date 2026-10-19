package com.cloudrive.fileService.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件对象实体类
 * <p>存储去重后的物理文件信息，是秒传功能的核心。
 * 多个用户上传相同文件时（SHA256哈希一致），只需存储一份物理文件，
 * 通过引用计数（refCount）管理文件的引用关系，用于垃圾回收。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class FileObject {
    /** 主键，自增ID */
    private Long id;
    /** 文件的SHA256哈希值，秒传去重的唯一标识 */
    private String sha256Hash;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 文件在存储系统中的物理路径 */
    private String storagePath;
    /** 文件类型（MIME类型） */
    private String fileType;
    /** 文件后缀名 */
    private String fileSuffix;
    /** 文件状态（如：正常、待删除等） */
    private Integer status;
    /** 引用计数，记录有多少用户引用了该文件，用于垃圾回收 */
    private Integer refCount;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
