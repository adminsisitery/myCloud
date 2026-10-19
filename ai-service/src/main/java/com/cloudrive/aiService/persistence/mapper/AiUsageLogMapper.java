package com.cloudrive.aiService.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.aiService.persistence.entity.AiUsageLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI Token 用量日志 Mapper
 *
 * @author 飞飞
 * @date 2026/8/2
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    /** 总览：指定时间范围内的总量统计 */
    Map<String, Object> costSummary(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 按 model + scene 分组统计 */
    List<Map<String, Object>> costByModel(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** 按天分组统计（趋势图） */
    List<Map<String, Object>> costDaily(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
