package com.ff.mycloud.ai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    /** 主键，自增ID */
    private Long id;

    /** 对话id */
    private Long conversationId;

    /** 规则 */
    private String role;

    /** 对话内容 */
    private String content;

    /** 输入 token 数 */
    private Integer inputTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 创建时间 */
    private LocalDateTime createTime;

}
