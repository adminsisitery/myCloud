package com.cloudrive.fileAsyncService.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * PG 向量同步消息体（与单体一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgSender implements Serializable {

    /** 待向量化的文本 */
    private String text;

    private Long userId;

    private Long fileId;

    /** 是否为索引重建（true 时由消费端从 OSS 重新提取文本） */
    private boolean rebuild;

    /** 文件内容 hash，用于避免过期消息覆盖新内容 */
    private String contentHash;
}
