package com.cloudrive.aiService.persistence.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_ai_message")
public class AiMessage {
    /** 主键，自增ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对话id */
    @TableField("conversation_id")
    private Long conversationId;

    /** 规则 */
    @TableField("role")
    private String role;

    /** 对话内容 */
    @TableField("content")
    private String content;

    /** 输入 token 数（仅 ASSISTANT 消息有值） */
    @TableField("input_tokens")
    private Integer inputTokens;

    /** 输出 token 数（仅 ASSISTANT 消息有值） */
    @TableField("output_tokens")
    private Integer outputTokens;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

}
