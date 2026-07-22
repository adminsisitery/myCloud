package com.ff.mycloud.ai.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_message")
public class AiMessages {
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

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

}
