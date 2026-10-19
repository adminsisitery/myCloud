package com.cloudrive.aiService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.aiService.client.UserSpaceClient;
import com.cloudrive.aiService.model.entity.UserCenter;
import com.cloudrive.aiService.repositorys.UserCenterMapper;
import com.cloudrive.aiService.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

/**
 * 用户空间服务实现
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class UserCenterMapperServiceImpl implements UserCenterMapperService {

    private final UserCenterMapper userCenterMapper;
    private final UserSpaceClient userSpaceClient;

    public UserCenterMapperServiceImpl(UserCenterMapper userCenterMapper,
                                       UserSpaceClient userSpaceClient) {
        this.userCenterMapper = userCenterMapper;
        this.userSpaceClient = userSpaceClient;
    }

    @Override
    public UserCenter getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return userCenterMapper.selectOne(new LambdaQueryWrapper<UserCenter>()
                .eq(UserCenter::getUserId, userId)
                .last("limit 1"));
    }

    @Override
    public boolean reserveSpace(long userId, long size) {
        return userSpaceClient.reserveSpace(userId, size);
    }

    @Override
    public void releaseSpace(Long userId, long size) {
        if (userId != null && size > 0) {
            userSpaceClient.releaseSpace(userId, size);
        }
    }
}
