package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.PasswordUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.UserCenterMapper;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.service.UserCenterMapperService;
import com.ff.mycloud.service.UserCenterService;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/8 19:38
 * @version 1.0
 */

@Service
public class UserCenterServiceImpl implements UserCenterService {

    UserCenterMapperService userCenterMapperService;
    UserCenterMapper userCenterMapper;
    UserMapperService userMapperService;
    public UserCenterServiceImpl(UserCenterMapperService userCenterMapperService, UserCenterMapper userCenterMapper, UserMapperService userMapperService) {
        this.userCenterMapperService = userCenterMapperService;
        this.userCenterMapper = userCenterMapper;
        this.userMapperService = userMapperService;
    }

    @Override
    public UserCenter getUserCenterInfo() {
        Long currentUserId = UserContext.getCurrentUserId();
        return userCenterMapperService.getByUserId(currentUserId);
    }

    @Override
    public Result<String> changePassword(String oldPassword, String newPassword) {
        Optional<User> byUserId = userMapperService.findByUserId(UserContext.getCurrentUserId());
        if (byUserId.isEmpty()) {
            return Result.error("用户不存在");
        }
        User user = byUserId.get();
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            return Result.error("用户原密码不正确");
        }
        String encode = PasswordUtil.encode(newPassword);
        user.setPassword(encode);
        userMapperService.updateById(user);
        return Result.success("修改成功");
    }
}
