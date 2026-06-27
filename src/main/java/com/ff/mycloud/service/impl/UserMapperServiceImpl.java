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
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/28 16:18
 * @version 1.0
 */

@Service
public class UserMapperServiceImpl extends ServiceImpl<UserMapper, User> implements UserMapperService {

    @Override
    public void updataUserRole(UserRole userRole) {
        Long userId = userRole.getUserId();
        Long roleId = userRole.getRoleId();
        if (roleId!= CommonConstants.UserRole.USER_ID && userId!= CommonConstants.UserRole.ADMIN_ID){
            return;
        }
        baseMapper.updateUserRole(userId,roleId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getUsername, username.trim())
                .oneOpt();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getEmail, email.trim())
                .oneOpt();
    }

    @Override
    public Optional<User> findByUserId(Long id) {
        return lambdaQuery()
                .eq(User::getId, id)
                .oneOpt();
    }

    @Override
    public User findByGithubId(String githubId) {
        return lambdaQuery().eq(User::getGithubId, githubId).one();
    }

    @Override
    public List<String> getRoleById(long id) {
        return baseMapper.selectRoleCodeByUserId(id);
    }

    @Override
    public void SetUserRole(Long userId, Long roleId) {
        baseMapper.setUserRole(userId,roleId);
    }

    public User findOneByUserId(Long id) {
        return lambdaQuery()
                .eq(User::getId, id)
                .one();
    }
}
