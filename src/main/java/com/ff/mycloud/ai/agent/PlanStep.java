package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 任务步骤 —— 规划中的一个可执行单元。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanStep {

    /** 步骤序号（0-based） */
    private int index;

    /** 步骤描述，如"列出根目录文件找到图片文件夹" */
    private String description;

    /** PENDING → RUNNING → DONE → FAILED */
    private String status;

    /** 执行结果文本 */
    private String result;

    public static PlanStep pending(int index, String description) {
        return PlanStep.builder()
                .index(index)
                .description(description)
                .status("PENDING")
                .build();
    }
}
