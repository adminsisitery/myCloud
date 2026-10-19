package com.cloudrive.pan_common.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录用户上下文。
 * <p>
 * 只回答"当前请求是谁"，不查库，也不依赖任何业务服务的仓储层。
 * 网关（{@code MyGlobalFilter}）已完成令牌校验，Sa-Token 会话又共享同一份 Redis，
 * 因此每个微服务都能直接取到登录用户 ID。
 * </p>
 * <p>
 * 需要用户实体时，由所在服务自己查询，或通过服务间调用获取。
 * 公共库反向依赖业务模块会形成循环，是拆分时最容易踩的坑。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public final class UserContext {

    private UserContext() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前登录用户的 ID
     * @throws cn.dev33.satoken.exception.NotLoginException 当前请求未携带有效令牌时抛出
     */
    public static Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }

    /**
     * 获取当前登录用户 ID，未登录时返回 {@code null}，供允许匿名访问的接口使用。
     *
     * @return 当前登录用户 ID，或 {@code null}
     */
    public static Long getCurrentUserIdOrNull() {
        return StpUtil.isLogin() ? getCurrentUserId() : null;
    }
}
