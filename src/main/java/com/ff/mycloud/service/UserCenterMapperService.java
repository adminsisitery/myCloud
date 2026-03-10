package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.UserCenter;
import org.springframework.stereotype.Service;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/7 17:07
 * @version 1.0
 */


public interface UserCenterMapperService extends IService<UserCenter> {
    UserCenter getByUserId(Long currentUserId);
}
