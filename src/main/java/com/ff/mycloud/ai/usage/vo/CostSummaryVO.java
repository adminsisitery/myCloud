package com.ff.mycloud.ai.usage.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 成本总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostSummaryVO {
    /** Token 总数 */
    private Long totalTokens;
    /** 估算费用（元） */
    private Double estimatedCost;
    /** API 调用次数 */
    private Long callCount;
    /** 平均延迟（毫秒） */
    private Double avgLatencyMs;
    /** 消耗最多的模型 */
    private String topModel;
    /** 消耗最多的场景 */
    private String topScene;
}
