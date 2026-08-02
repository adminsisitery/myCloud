package com.ff.mycloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.ai.model.entity.AiUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Token 用量日志 Mapper
 *
 * @author 飞飞
 * @date 2026/8/2
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {
}
