package com.cloudrive.aiService.usage.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按天维度的费用趋势
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCostVO {
    /** 日期（yyyy-MM-dd） */
    private String date;
    /** Token 总数 */
    private Long totalTokens;
    /** 估算费用（元） */
    private Double estimatedCost;
    /** 调用次数 */
    private Long callCount;
    /** 平均延迟（毫秒） */
    private Double avgLatencyMs;
}
