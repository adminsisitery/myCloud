package com.cloudrive.aiService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.aiService.model.entity.UserCenter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户空间 Mapper（只读：用于 Agent getStorageInfo 查询已用/总空间）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface UserCenterMapper extends BaseMapper<UserCenter> {
}
