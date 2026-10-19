package com.cloudrive.pan_common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * PG 向量同步消息（跨服务共享）
 * <p>
 * {@code rebuild=true} 表示消息不携带文本，消费端从 OSS 重新提取文本后重建向量索引，
 * 适用于分片上传、直传等生产端拿不到本地文本的场景。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PgIndexMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** 文件所有者 */
    private Long userId;

    /** 待向量化文本（rebuild=true 时可为空） */
    private String text;

    /** 文件内容 hash，消费端用于丢弃过期索引消息 */
    private String contentHash;

    /** 是否由消费端从 OSS 重建文本 */
    private boolean rebuild;
}
