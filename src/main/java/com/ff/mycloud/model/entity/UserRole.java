package com.ff.mycloud.model.entity;

import lombok.Data;

/**
 * 用户角色关联实体类
 * <p>记录用户与角色的多对多关联关系，用于实现基于角色的权限控制（RBAC）。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class UserRole {
    /** 用户ID */
    public Long userId;
    /** 角色ID */
    public Long roleId;
}
