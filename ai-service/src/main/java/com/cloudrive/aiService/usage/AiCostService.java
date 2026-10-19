package com.cloudrive.aiService.usage;

import com.cloudrive.aiService.usage.vo.CostSummaryVO;
import com.cloudrive.aiService.usage.vo.DailyCostVO;
import com.cloudrive.aiService.usage.vo.ModelCostVO;
import java.util.List;

/**
 * AI Token 成本统计服务
 */
public interface AiCostService {

    /** 总览：指定时间范围内的消耗概况 */
    CostSummaryVO getSummary(String range);

    /** 按模型 + 场景维度的费用明细 */
    List<ModelCostVO> getByModel(String range);

    /** 按天的费用趋势 */
    List<DailyCostVO> getDaily(String range);
}
