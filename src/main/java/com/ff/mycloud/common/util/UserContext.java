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
 * 用户上下文工具类，提供获取当前登录用户ID和用户实体的静态方法。
 * 通过@PostConstruct初始化静态引用，使得静态方法可以调用Spring托管的Service Bean。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Component
public class UserContext {

    // 注入Service接口（而不是直接注入Mapper）
    @Autowired
    private UserMapperService userMapperService;

    // 静态变量，用于在静态方法中访问Service
    private static UserContext userContext;

    /**
     * 初始化静态变量，使静态方法能访问Spring管理的Service Bean。
     * 在Spring容器实例化该Bean后自动调用。
     */
    @PostConstruct
    public void init() {
        userContext = this;
        userContext.userMapperService = this.userMapperService;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 当前登录用户的ID
     */
    public static Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }

    /**
     * 获取当前登录用户的完整实体信息。
     * 通过Service层查询，如果用户不存在则抛出USER_NOT_FOUND业务异常。
     *
     * @return 当前登录用户的完整实体
     * @throws com.ff.mycloud.common.exceotion.BusinessException 当用户不存在时抛出
     */
    public static User getCurrentUser() {
        Long currentUserId = getCurrentUserId();
        // 调用Service层的业务方法（你原来的findByUserId）
        Optional<User> user = userContext.userMapperService.findByUserId(currentUserId);
        // 用你现有的ExceptionUtil判空
        ExceptionUtil.throwIfNull(user.orElse(null), ErrorCode.USER_NOT_FOUND);
        return user.get();
    }
}