package com.ff.mycloud.ai.usage;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ff.mycloud.ai.usage.vo.CostSummaryVO;
import com.ff.mycloud.ai.usage.vo.DailyCostVO;
import com.ff.mycloud.ai.usage.vo.ModelCostVO;
import com.ff.mycloud.common.result.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Token 成本统计控制器（管理员专用）
 */
@RestController
@RequestMapping("/admin/ai/cost")
@SaCheckRole("admin")
public class AiCostController {

    private final AiCostService aiCostService;

    public AiCostController(AiCostService aiCostService) {
        this.aiCostService = aiCostService;
    }

    /** 总览：总 token、估算费用、调用次数、平均延迟 */
    @GetMapping("/summary")
    public Result<CostSummaryVO> summary(@RequestParam(defaultValue = "7d") String range) {
        return Result.success(aiCostService.getSummary(range));
    }

    /** 按模型 + 场景分组的消耗明细 */
    @GetMapping("/by-model")
    public Result<List<ModelCostVO>> byModel(@RequestParam(defaultValue = "7d") String range) {
        return Result.success(aiCostService.getByModel(range));
    }

    /** 按天的消耗趋势 */
    @GetMapping("/daily")
    public Result<List<DailyCostVO>> daily(@RequestParam(defaultValue = "7d") String range) {
        return Result.success(aiCostService.getDaily(range));
    }
}
