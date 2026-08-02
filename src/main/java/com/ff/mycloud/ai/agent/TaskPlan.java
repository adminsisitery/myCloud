package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Agent 任务计划 —— 规划阶段产出的完整执行计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskPlan {

    /** 唯一计划ID */
    @Builder.Default
    private String planId = UUID.randomUUID().toString();

    /** 用户原始目标 */
    private String goal;

    /** 规划步骤列表 */
    private List<PlanStep> steps;
}
