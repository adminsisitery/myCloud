package com.ff.mycloud.model.entity;

import lombok.Data;

import java.util.Map;
import java.util.Set;

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
    /** 已完成上传的分片编号集合 */
    private Set<Integer> completedParts;
    /** 分片编号 → ETag 映射 */
    private Map<Integer, String> partEtags;
    /** 文件SHA-256哈希 */
    private String sha256Hash;
    /** 是否已预分配空间 */
    private Boolean spacePreAllocated;
    /** 创建时间戳（毫秒） */
    private Long createdAt;
}
