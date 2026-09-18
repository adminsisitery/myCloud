package com.ff.mycloud.model.dto;

import lombok.Data;

/**
 * 秒传执行请求 DTO
 * <p>
 * 由 {@code POST /api/files/fast-upload} 接收，是「秒传判定」之后的那一半：
 * 客户端已通过 {@code POST /api/files/check} 得知云端存在同哈希同大小的文件，
 * 本请求要求服务端在目标目录直接建一条逻辑记录，<b>不再传输文件字节</b>。
 * </p>
 * <p>
 * 注意：服务端不会信任本请求里的任何字段，会用 sha256Hash + fileSize 重新查库确认
 * 物理对象确实存在，避免 check 与 fast-upload 之间源文件被删除导致空记录落库。
 * </p>
 *
 * @author 飞飞
 * @date 2026/09/18
 */
@Data
public class FastUploadRequest {

    /** 原始文件名（含后缀），落库时作为 originalFilename 保存 */
    private String fileName;

    /** 文件大小（字节），与 sha256Hash 一起构成秒传判据 */
    private Long fileSize;

    /** 目标父目录ID，0 表示根目录 */
    private Long parentId;

    /** 文件 SHA-256（64 位十六进制），由客户端计算 */
    private String sha256Hash;

    /** 上传任务ID，可选；有值时把进度直接置为完成 */
    private String taskId;
}
