package com.ff.mycloud.saToken;

import cn.dev33.satoken.stp.StpInterface;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SaToken权限与角色接口实现类。
 * 实现SaToken框架的StpInterface接口，提供用户权限列表和角色列表的获取功能。
 * 通过注入UserMapperService从数据库中查询用户角色信息。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /** 用户数据访问服务，用于查询用户角色信息 */
    private final UserMapperService userMapperService;

    /**
     * 构造方法，通过构造器注入UserMapperService实例。
     *
     * @param userMapperService 用户数据访问服务
     */
    public StpInterfaceImpl(UserMapperService userMapperService) {
        this.userMapperService = userMapperService;
    }

    /**
     * 获取指定用户的权限码列表。
     * 当前实现返回空列表，暂未启用细粒度权限控制。
     *
     * @param o 用户标识（通常为用户ID）
     * @param s 登录类型
     * @return 权限码列表，当前返回空列表
     */
    @Override
    public List<String> getPermissionList(Object o, String s) {
        return List.of();
    }

    /**
     * 获取指定用户的角色列表。
     * 根据用户ID从数据库中查询角色信息，若查询结果为空则默认返回"user"角色。
     *
     * @param o 用户标识（通常为用户ID）
     * @param s 登录类型
     * @return 角色名称列表，不为null
     * @throws NumberFormatException 如果用户标识无法解析为长整型数字
     */
    @Override
    public List<String> getRoleList(Object o, String s) {
        long id = Long.parseLong(o.toString());
        List<String> roleById = userMapperService.getRoleById(id);
        if (roleById == null) {
            return List.of("user");
        }
        return roleById;
    }
}
