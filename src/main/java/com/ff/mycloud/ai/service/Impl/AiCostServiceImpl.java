package com.ff.mycloud.ai.service.impl;

import com.ff.mycloud.ai.config.AIProperties;
import com.ff.mycloud.ai.mapper.AiUsageLogMapper;
import com.ff.mycloud.ai.model.vo.CostSummaryVO;
import com.ff.mycloud.ai.model.vo.DailyCostVO;
import com.ff.mycloud.ai.model.vo.ModelCostVO;
import com.ff.mycloud.ai.service.AiCostService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiCostServiceImpl implements AiCostService {

    private final AiUsageLogMapper mapper;
    private final Map<String, AIProperties.Pricing> pricingMap;

    public AiCostServiceImpl(AiUsageLogMapper mapper, AIProperties aiProperties) {
        this.mapper = mapper;
        // 构建 model name → pricing 映射
        this.pricingMap = aiProperties.getModels().stream()
                .collect(Collectors.toMap(
                        AIProperties.ModelConfig::getName,
                        AIProperties.ModelConfig::getPricing,
                        (a, b) -> a));
    }

    @Override
    public CostSummaryVO getSummary(String range) {
        LocalDateTimeRange r = parseRange(range);
        Map<String, Object> row = mapper.costSummary(r.start, r.end);

        long totalTokens = toLong(row.get("totalTokens"));
        long promptTokens = toLong(row.get("promptTokens"));
        long completionTokens = toLong(row.get("completionTokens"));
        long callCount = toLong(row.get("callCount"));
        double avgLatency = toDouble(row.get("avgLatencyMs"));

        // 平均估算费用（所有模型混算，取平均价格）
        double avgInputPrice = avg(pricingMap.values().stream()
                .mapToDouble(AIProperties.Pricing::getInputPrice));
        double avgOutputPrice = avg(pricingMap.values().stream()
                .mapToDouble(AIProperties.Pricing::getOutputPrice));
        double cost = tokensToCost(promptTokens, avgInputPrice)
                    + tokensToCost(completionTokens, avgOutputPrice);

        // 按 model+scene 聚合找 top
        List<Map<String, Object>> byModel = mapper.costByModel(r.start, r.end);
        String topModel = topBy(byModel, "model", "totalTokens");
        String topScene = topBy(byModel, "scene", "totalTokens");

        return CostSummaryVO.builder()
                .totalTokens(totalTokens)
                .estimatedCost(round2(cost))
                .callCount(callCount)
                .avgLatencyMs(round2(avgLatency))
                .topModel(topModel)
                .topScene(topScene)
                .build();
    }

    @Override
    public List<ModelCostVO> getByModel(String range) {
        LocalDateTimeRange r = parseRange(range);
        List<Map<String, Object>> rows = mapper.costByModel(r.start, r.end);
        List<ModelCostVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String model = (String) row.get("model");
            long prompt = toLong(row.get("promptTokens"));
            long completion = toLong(row.get("completionTokens"));
            AIProperties.Pricing pricing = pricingMap.getOrDefault(model,
                    new AIProperties.Pricing());
            double cost = tokensToCost(prompt, pricing.getInputPrice())
                        + tokensToCost(completion, pricing.getOutputPrice());

            result.add(ModelCostVO.builder()
                    .model(model)
                    .scene((String) row.get("scene"))
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .totalTokens(toLong(row.get("totalTokens")))
                    .estimatedCost(round2(cost))
                    .callCount(toLong(row.get("callCount")))
                    .build());
        }
        return result;
    }

    @Override
    public List<DailyCostVO> getDaily(String range) {
        LocalDateTimeRange r = parseRange(range);
        List<Map<String, Object>> rows = mapper.costDaily(r.start, r.end);
        List<DailyCostVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(DailyCostVO.builder()
                    .date((String) row.get("date"))
                    .totalTokens(toLong(row.get("totalTokens")))
                    .callCount(toLong(row.get("callCount")))
                    .avgLatencyMs(round2(toDouble(row.get("avgLatencyMs"))))
                    .estimatedCost(null) // 按天不计算具体费用（模型混在一起）
                    .build());
        }
        // 用平均价格补算每日费用
        double avgInputPrice = avg(pricingMap.values().stream()
                .mapToDouble(AIProperties.Pricing::getInputPrice));
        double avgOutputPrice = avg(pricingMap.values().stream()
                .mapToDouble(AIProperties.Pricing::getOutputPrice));

        // 粗略按 1:1 的 input:output 比例估算每日费用
        double blendedPrice = (avgInputPrice + avgOutputPrice) / 2.0;
        for (DailyCostVO vo : result) {
            vo.setEstimatedCost(round2(tokensToCost(vo.getTotalTokens(), blendedPrice)));
        }
        return result;
    }

    // ==================== 工具方法 ====================

    /** 解析 range 字符串 */
    private LocalDateTimeRange parseRange(String range) {
        int days = switch (range) {
            case "30d" -> 30;
            case "90d" -> 90;
            default -> 7; // 默认 7 天
        };
        return new LocalDateTimeRange(
                LocalDate.now().minusDays(days).atStartOfDay(),
                LocalDateTime.now()
        );
    }

    /** Map 值转 long，兼容 BigDecimal/Integer/Long */
    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** tokens → 费用：（tokens / 1_000_000）× 单价 */
    private double tokensToCost(long tokens, double pricePerMillion) {
        return tokens / 1_000_000.0 * pricePerMillion;
    }

    private double avg(java.util.stream.DoubleStream s) {
        return s.average().orElse(0.0);
    }

    /** 从 model+scene 分组结果中找某字段的 top1 */
    private String topBy(List<Map<String, Object>> rows, String groupField, String sumField) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.get(groupField),
                        Collectors.summingLong(r -> toLong(r.get(sumField)))))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("-");
    }

    private record LocalDateTimeRange(LocalDateTime start, LocalDateTime end) {}
}
