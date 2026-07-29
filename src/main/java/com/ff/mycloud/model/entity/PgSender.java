package com.ff.mycloud.model.entity;

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
}
