package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.repositorys.UserCenterMapper;
import com.ff.mycloud.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

@Service
public class UserCenterMapperServiceImpl extends ServiceImpl<UserCenterMapper, UserCenter> implements UserCenterMapperService {

    @Override
    public UserCenter getByUserId(Long currentUserId) {
        return lambdaQuery()
                .eq(UserCenter::getUserId, currentUserId)
                .one();
    }

    @Override
    public void releaseSpace(Long currentUserId, long size) {
        baseMapper.releaseSpace(currentUserId, size);
    }

    @Override
    public boolean reserveSpace(long userId, long fileSize) {
        return baseMapper.reserveSpace(userId, fileSize) > 0;
    }
}
