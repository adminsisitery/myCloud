package com.ff.mycloud.ai.usage.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按模型 + 场景维度的费用拆解
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCostVO {
    /** 模型名 */
    private String model;
    /** 场景：chat / agent */
    private String scene;
    /** 输入 token 数 */
    private Long promptTokens;
    /** 输出 token 数 */
    private Long completionTokens;
    /** Token 总数 */
    private Long totalTokens;
    /** 估算费用（元） */
    private Double estimatedCost;
    /** 调用次数 */
    private Long callCount;
}
