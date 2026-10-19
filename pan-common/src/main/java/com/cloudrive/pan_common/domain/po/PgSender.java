package com.cloudrive.pan_common.domain.po;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class PgSender implements Serializable {
    /** 文档原始文本内容 */
    private String text;
    private Long userId;
    private Long fileId;
    /** Restore requests rebuild text from the original object instead of trusting an old index. */
    private boolean rebuild;
    private String contentHash;
}
