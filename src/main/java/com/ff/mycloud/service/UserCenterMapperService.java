package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.UserCenter;

public interface UserCenterMapperService extends IService<UserCenter> {

    UserCenter getByUserId(Long currentUserId);

    void releaseSpace(Long currentUserId, long size);

    boolean reserveSpace(long userId, long fileSize);
}
