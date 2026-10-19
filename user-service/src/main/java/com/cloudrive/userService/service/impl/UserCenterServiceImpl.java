package com.cloudrive.userService.service.impl;

import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.PasswordUtil;
import com.cloudrive.pan_common.util.UserContext;
import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.service.UserCenterMapperService;
import com.cloudrive.userService.service.UserCenterService;
import com.cloudrive.userService.service.UserMapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户中心服务实现：查询个人空间信息、修改密码。
 *
 * @author 飞飞
 * @date 2026/3/8
 */
@Service
@Slf4j
public class UserCenterServiceImpl implements UserCenterService {

    private final UserCenterMapperService userCenterMapperService;
    private final UserMapperService userMapperService;

    public UserCenterServiceImpl(UserCenterMapperService userCenterMapperService,
                                 UserMapperService userMapperService) {
        this.userCenterMapperService = userCenterMapperService;
        this.userMapperService = userMapperService;
    }

    /**
     * 获取当前登录用户的空间信息。
     *
     * @return 用户中心实体
     */
    @Override
    public UserCenter getUserCenterInfo() {
        return userCenterMapperService.getByUserId(UserContext.getCurrentUserId());
    }

    /**
     * 修改当前用户密码：校验用户存在 → 校验旧密码 → 加密并保存。
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 操作结果
     */
    @Override
    public Result<String> changePassword(String oldPassword, String newPassword) {
        Long userId = UserContext.getCurrentUserId();
        Optional<User> found = userMapperService.findByUserId(userId);
        if (found.isEmpty()) {
            return Result.error("用户不存在");
        }
        User user = found.get();
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            return Result.error("用户原密码不正确");
        }
        user.setPassword(PasswordUtil.encode(newPassword));
        userMapperService.updateById(user);
        log.info("密码修改成功: userId={}", userId);
        return Result.success("修改成功");
    }
}
