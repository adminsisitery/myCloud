package com.cloudrive.fileService.model.entity;

import lombok.Data;

@Data
public class ChunkTask {
    /** 自定义上传任务ID（UUID，暴露给前端） */
    private String uploadId;
    /** OSS MultipartUpload ID */
    private String ossUploadId;
    /** OSS最终文件路径（init时生成） */
    private String objectKey;
    /** 用户ID */
    private Long userId;
    /** 文件原始名称 */
    private String fileName;
    /** 文件总大小（字节） */
    private Long fileSize;
    /** 文件MIME类型 */
    private String fileType;
    /** 父目录ID，0为根目录 */
    private Long parentId;
    /** 总分片数 */
    private Integer totalParts;
    /** 单分片大小（字节） */
    private Integer chunkSize;
    /** 文件SHA-256哈希 */
    private String sha256Hash;
    /** 是否已预分配空间 */
    private Boolean spacePreAllocated;
    /** OSS 分片是否已经合并；DB 入库失败重试时不再重复调用 completeMultipartUpload。 */
    private Boolean storageCompleted;
    /** 创建时间戳（毫秒） */
    private Long createdAt;
    /** 最近一次活动时间戳（毫秒），每次上传分片时更新，用于超时判断 */
    private Long lastActivityTime;
}
