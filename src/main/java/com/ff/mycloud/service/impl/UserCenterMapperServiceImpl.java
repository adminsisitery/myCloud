package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.repositorys.UserCenterMapper;
import com.ff.mycloud.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/7 17:08
 * @version 1.0
 */

@Service
public class UserCenterMapperServiceImpl extends ServiceImpl<UserCenterMapper, UserCenter> implements UserCenterMapperService {

    @Override
    public UserCenter getByUserId(Long currentUserId) {
        return lambdaQuery()
                .eq(UserCenter::getUserId, currentUserId)
                .one();
    }
}
