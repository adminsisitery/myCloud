package com.ff.mycloud.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileObject {
    private Long id;                // 主键自增
    private String sha256Hash;      // 文件hash（妙传核心）
    private Long fileSize;          // 文件大小
    private String storagePath;     // 存储路径
    private String fileType;        // 文件类型
    private String fileSuffix;      // 文件后缀
    private Integer status;         // 状态
    private Integer refCount;       // 引用计数（垃圾回收用）
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
}
