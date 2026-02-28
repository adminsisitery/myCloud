package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.repositorys.UserMapper;
import com.ff.mycloud.service.UserMapperService;
import org.springframework.stereotype.Service;

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
                .eq(User::getUsername, email.trim())
                .oneOpt();
    }

    @Override
    public Optional<User> findByUserId(Long id) {
        return lambdaQuery()
                .eq(User::getId, id)
                .oneOpt();
    }
}
