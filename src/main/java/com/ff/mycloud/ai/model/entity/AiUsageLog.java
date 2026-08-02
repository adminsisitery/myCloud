package com.ff.mycloud.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI Token 用量明细日志
 * <p>记录每次 AI API 调用的 token 消耗、延迟和场景信息，
 * 用于成本分析、性能监控和异常排查。</p>
 *
 * @author 飞飞
 * @date 2026/8/2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ai_usage_log")
public class AiUsageLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("model")
    private String model;

    @TableField("scene")
    private String scene;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("tool_call_count")
    private Integer toolCallCount;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
