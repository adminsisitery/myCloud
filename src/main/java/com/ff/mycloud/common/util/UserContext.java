package com.ff.mycloud.common.util;

import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.service.UserMapperService; // 注入你的Service接口
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cn.dev33.satoken.stp.StpUtil;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * @description: 用户上下文工具类
 * @author 飞飞
 * @date 2026/2/22 14:10
 * @version 1.0
 */
@Component
public class UserContext {

    // 注入Service接口（而不是直接注入Mapper）
    @Autowired
    private UserMapperService userMapperService;

    // 静态变量，用于在静态方法中访问Service
    private static UserContext userContext;

    // 初始化静态变量（关键：让静态方法能调用Spring管理的Service Bean）
    @PostConstruct
    public void init() {
        userContext = this;
        userContext.userMapperService = this.userMapperService;
    }

    // 获得当前登录用户ID（保持不变）
    public static Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }

    // 获得当前登录用户（调用Service层的业务方法，保留Optional）
    public static User getCurrentUser() {
        Long currentUserId = getCurrentUserId();
        // 调用Service层的业务方法（你原来的findByUserId）
        Optional<User> user = userContext.userMapperService.findByUserId(currentUserId);
        // 用你现有的ExceptionUtil判空
        ExceptionUtil.throwIfNull(user.orElse(null), ErrorCode.USER_NOT_FOUND);
        return user.get();
    }
}