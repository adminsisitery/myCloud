package com.ff.mycloud.service.impl;

import lombok.extern.slf4j.Slf4j;
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
 * 用户中心服务实现类
 * <p>提供用户中心信息的查询和密码修改功能。</p>
 *
 * @author 飞飞
 * @date 2026/3/8
 * @version 1.0
 */

@Service
@Slf4j
public class UserCenterServiceImpl implements UserCenterService {

    UserCenterMapperService userCenterMapperService;
    UserCenterMapper userCenterMapper;
    UserMapperService userMapperService;
    public UserCenterServiceImpl(UserCenterMapperService userCenterMapperService, UserCenterMapper userCenterMapper, UserMapperService userMapperService) {
        this.userCenterMapperService = userCenterMapperService;
        this.userCenterMapper = userCenterMapper;
        this.userMapperService = userMapperService;
    }

    /**
     * 获取当前登录用户的中心信息（包含已用空间、总空间等）
     *
     * @return 当前用户的UserCenter实体
     */
    @Override
    public UserCenter getUserCenterInfo() {
        Long currentUserId = UserContext.getCurrentUserId();
        return userCenterMapperService.getByUserId(currentUserId);
    }

    /**
     * 修改当前用户密码
     * <p>流程：校验用户存在 -> 校验旧密码正确 -> 加密新密码并更新。</p>
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    @Override
    public Result<String> changePassword(String oldPassword, String newPassword) {
        Optional<User> byUserId = userMapperService.findByUserId(UserContext.getCurrentUserId());
        if (byUserId.isEmpty()) {
            return Result.error("用户不存在");
        }
        User user = byUserId.get();
        // 校验旧密码是否匹配
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            return Result.error("用户原密码不正确");
        }
        // 加密新密码并保存
        String encode = PasswordUtil.encode(newPassword);
        user.setPassword(encode);
        userMapperService.updateById(user);
        log.info("密码修改成功: userId={}", UserContext.getCurrentUserId());
        return Result.success("修改成功");
    }
}
