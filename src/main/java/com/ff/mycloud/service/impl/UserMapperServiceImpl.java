package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserRole;
import com.ff.mycloud.repositorys.UserMapper;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户 Mapper 服务实现类
 * <p>基于MyBatis-Plus实现用户表的数据库操作，包括查询（按用户名、邮箱、ID、GitHub ID）
 * 和角色管理（更新角色、设置角色、查询角色）等功能。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 * @version 1.0
 */

@Service
public class UserMapperServiceImpl extends ServiceImpl<UserMapper, User> implements UserMapperService {

    /**
     * 更新用户角色
     * <p>保护机制：普通用户ID和管理员ID不可被修改角色。</p>
     *
     * @param userRole 用户角色实体
     */
    @Override
    public void updataUserRole(UserRole userRole) {
        Long userId = userRole.getUserId();
        Long roleId = userRole.getRoleId();
        // 保护管理员和普通用户的默认角色不被修改
        if (roleId!= CommonConstants.UserRole.USER_ID && userId!= CommonConstants.UserRole.ADMIN_ID){
            return;
        }
        baseMapper.updateUserRole(userId,roleId);
    }

    /**
     * 根据用户名查找用户（空值安全）
     *
     * @param username 用户名
     * @return 用户实体Optional，不存在或用户名为空时返回empty
     */
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getUsername, username.trim())
                .oneOpt();
    }

    /**
     * 根据邮箱查找用户（空值安全）
     *
     * @param email 邮箱地址
     * @return 用户实体Optional，不存在或邮箱为空时返回empty
     */
    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getEmail, email.trim())
                .oneOpt();
    }

    /**
     * 根据用户ID查找用户
     *
     * @param id 用户ID
     * @return 用户实体Optional
     */
    @Override
    public Optional<User> findByUserId(Long id) {
        return lambdaQuery()
                .eq(User::getId, id)
                .oneOpt();
    }

    /**
     * 根据GitHub ID查找用户
     *
     * @param githubId GitHub用户ID
     * @return 用户实体，不存在则返回null
     */
    @Override
    public User findByGithubId(String githubId) {
        return lambdaQuery().eq(User::getGithubId, githubId).one();
    }

    /**
     * 获取用户的角色编码列表
     *
     * @param id 用户ID
     * @return 角色编码列表
     */
    @Override
    public List<String> getRoleById(long id) {
        return baseMapper.selectRoleCodeByUserId(id);
    }

    /**
     * 为用户设置角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Override
    public void SetUserRole(Long userId, Long roleId) {
        baseMapper.setUserRole(userId,roleId);
    }

    /**
     * 根据用户ID查找单个用户（非Optional返回）
     *
     * @param id 用户ID
     * @return 用户实体，不存在则返回null
     */
    public User findOneByUserId(Long id) {
        return lambdaQuery()
                .eq(User::getId, id)
                .one();
    }
}
