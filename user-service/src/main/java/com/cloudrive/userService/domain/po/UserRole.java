package com.cloudrive.userService.domain.po;

import lombok.Data;

/**
 * 用户与角色的关联关系。
 * <p>
 * 对应表 t_user_role，一个用户可以拥有多个角色（RBAC）。
 * 与 {@link com.cloudrive.pan_common.constant.CommonConstants.UserRole} 区分：
 * 后者是角色 ID 常量（ADMIN_ID / USER_ID），本类是请求体与数据库行。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class UserRole {

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
