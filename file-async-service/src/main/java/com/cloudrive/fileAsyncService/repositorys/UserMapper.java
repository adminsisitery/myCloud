package com.cloudrive.fileAsyncService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileAsyncService.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户只读 Mapper（用量通知查询邮箱）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
