package com.cloudrive.userService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserRole;
import com.cloudrive.userService.mapper.UserMapper;
import com.cloudrive.userService.service.UserMapperService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户表的领域服务实现，底层是 {@link UserMapper}（MyBatis-Plus BaseMapper）。
 *
 * @author 飞飞
 * @date 2026/2/28
 */
@Service
public class UserMapperServiceImpl extends ServiceImpl<UserMapper, User> implements UserMapperService {

    /**
     * 覆盖用户已有角色。
     * <p>
     * 说明：原实现用 {@code CommonConstants.UserRole.USER_ID / ADMIN_ID} 与 {@code userId} 比较，
     * 但这两个常量表示的是「角色 ID」，不是「用户 ID」，判断语义是错的，会导致绝大多数请求被直接 return。
     * 这里先改为只做空值保护，把权限校验留给 {@code @SaCheckRole("admin")} 在上层完成。
     * </p>
     *
     * @param userRole 用户与角色的关联
     */
    @Override
    public void updateUserRole(UserRole userRole) {
        Long userId = userRole.getUserId();
        Long roleId = userRole.getRoleId();
        if (userId == null || roleId == null) {
            return;
        }
        baseMapper.updateUserRole(userId, roleId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getUsername, username.trim())
                .oneOpt();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getEmail, email.trim())
                .oneOpt();
    }

    @Override
    public Optional<User> findByUserId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return lambdaQuery()
                .eq(User::getId, id)
                .oneOpt();
    }

    @Override
    public User findByGithubId(String githubId) {
        if (githubId == null || githubId.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(User::getGithubId, githubId)
                .one();
    }

    @Override
    public List<String> getRoleById(long id) {
        return baseMapper.selectRoleCodeByUserId(id);
    }

    @Override
    public void setUserRole(Long userId, Long roleId) {
        baseMapper.setUserRole(userId, roleId);
    }
}
