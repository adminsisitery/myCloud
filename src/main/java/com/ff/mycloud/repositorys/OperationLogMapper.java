package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志Mapper接口
 * 功能描述：提供操作日志的基础CRUD数据访问操作
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
