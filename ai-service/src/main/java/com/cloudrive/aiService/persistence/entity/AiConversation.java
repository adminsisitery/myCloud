package com.cloudrive.aiService.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI会话实体类
 * <p>映射数据库表 t_ai_conversation，记录用户与AI的对话会话元数据。
 * 每个用户可以有多个会话，每个会话包含多条消息。</p>
 *
 * @author 飞飞
 * @date 2026/7/11
 */
@Data
@TableName("t_ai_conversation")
public class AiConversation {

    @TableField("mode")
    private String mode;

    /** 主键，自增ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    @TableField("user_id")
    private Long userId;

    /** 会话标题（默认取首条用户消息截断） */
    @TableField("title")
    private String title;

    /** 使用的AI模型名称 */
    @TableField("model")
    private String model;

    @TableField("count")
    private Long count;

    /** 累计 token 消耗 */
    @TableField("total_tokens")
    private Long totalTokens;

    /** 会话创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 会话最后活跃时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 完整对话记忆 JSON（含工具调用，供 LC4j ChatMemoryStore 使用） */
    @TableField("memory_state")
    private String memoryState;
}
