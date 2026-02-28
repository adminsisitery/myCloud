package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/28 16:14
 * @version 1.0
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
