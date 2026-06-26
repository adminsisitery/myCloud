package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserMapperService extends IService<User> {
    void updataUserRole(UserRole userRole);

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUserId(Long id);

    User findByGithubId(String githubId);

    List<String> getRoleById(long id);

    void SetUserRole(Long userId, Long roleId);
}
