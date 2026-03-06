package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.User;

import java.util.Optional;

public interface UserMapperService extends IService<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUserId(Long id);

    User findByGithubId(String githubId);
}
