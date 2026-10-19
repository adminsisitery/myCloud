package com.cloudrive.userService.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.constant.FileConstant;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.EmailUtil;
import com.cloudrive.pan_common.util.ExceptionUtil;
import com.cloudrive.pan_common.util.IpUtil;
import com.cloudrive.pan_common.util.PasswordUtil;
import com.cloudrive.pan_common.util.UserContext;
import com.cloudrive.userService.convert.UserCenterConverter;
import com.cloudrive.userService.domain.dto.LoginDTO;
import com.cloudrive.userService.domain.dto.RegisterDTO;
import com.cloudrive.userService.domain.po.User;
import com.cloudrive.userService.domain.po.UserCenter;
import com.cloudrive.userService.domain.po.UserRole;
import com.cloudrive.userService.redis.LoginFailRedis;
import com.cloudrive.userService.redis.VerificationCodeRedis;
import com.cloudrive.userService.service.UserCenterMapperService;
import com.cloudrive.userService.service.UserMapperService;
import com.cloudrive.userService.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.cloudrive.pan_common.enums.ErrorCode.ACCOUNT_DISABLED;
import static com.cloudrive.pan_common.enums.ErrorCode.ACCOUNT_LOCKED;
import static com.cloudrive.pan_common.enums.ErrorCode.EMAIL_EXISTS;
import static com.cloudrive.pan_common.enums.ErrorCode.INVALID_PASSWORD;
import static com.cloudrive.pan_common.enums.ErrorCode.USERNAME_EXISTS;
import static com.cloudrive.pan_common.enums.ErrorCode.USER_NOT_FOUND;
import static com.cloudrive.pan_common.enums.ErrorCode.VERIFICATION_CODE_ERROR;

/**
 * 用户服务实现类。
 * <p>
 * 登录采用三层防护（限流 → IP 维度计数 → 账户维度渐进退避 + 硬锁）；
 * 注册包含验证码校验、用户创建、空间初始化与角色绑定。
 * </p>
 *
 * @author 飞飞
 * @date 2026/1/11
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 验证码取值范围：100000 ~ 999999，共 6 位 */
    private static final int CODE_ORIGIN = 100_000;
    private static final int CODE_BOUND = 900_000;

    private final SecureRandom random = new SecureRandom();

    private final UserMapperService userMapperService;
    private final UserCenterMapperService userCenterMapperService;
    private final UserCenterConverter userCenterConverter;
    private final VerificationCodeRedis verificationCodeRedis;
    private final LoginFailRedis loginFailRedis;

    public UserServiceImpl(UserMapperService userMapperService,
                           UserCenterMapperService userCenterMapperService,
                           UserCenterConverter userCenterConverter,
                           VerificationCodeRedis verificationCodeRedis,
                           LoginFailRedis loginFailRedis) {
        this.userMapperService = userMapperService;
        this.userCenterMapperService = userCenterMapperService;
        this.userCenterConverter = userCenterConverter;
        this.verificationCodeRedis = verificationCodeRedis;
        this.loginFailRedis = loginFailRedis;
    }

    /**
     * 用户登录。
     * <p>
     * 失败计数先按 {@code userId = -1} 记在 IP 维度，密码校验失败时再用真实 userId 补记账户维度，
     * 这样用户名不存在与密码错误对外表现一致，不会泄露账号是否注册。
     * </p>
     *
     * @param loginDTO 登录参数
     * @return Sa-Token 令牌
     */
    @Override
    public String login(LoginDTO loginDTO) {
        String ip = IpUtil.getClientIp();

        Optional<User> found = userMapperService.findByUsername(loginDTO.getUsername());
        Long userId = found.map(User::getId).orElse(-1L);

        long[] status = loginFailRedis.recordAndCheck(userId, ip);
        if (status[0] == 1) {
            log.warn("登录被拦截: userId={}, ip={}, failCount={}", userId, ip, status[1]);
            ExceptionUtil.throwBizException(ACCOUNT_LOCKED);
        }

        if (found.isEmpty()) {
            log.warn("登录失败: reason=user_not_found");
            ExceptionUtil.throwBizException(USER_NOT_FOUND);
        }
        User user = found.get();

        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            loginFailRedis.recordAndCheck(user.getId(), ip);
            log.warn("登录失败: reason=invalid_password, userId={}", user.getId());
            ExceptionUtil.throwBizException(INVALID_PASSWORD);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败: reason=account_disabled, userId={}", user.getId());
            ExceptionUtil.throwBizException(ACCOUNT_DISABLED);
        }

        loginFailRedis.reset(user.getId(), ip);

        StpUtil.login(user.getId());
        log.info("登录成功: userId={}", user.getId());
        return StpUtil.getTokenValue();
    }

    /**
     * 注册：校验用户名/邮箱唯一 → 校验验证码 → 建用户 → 初始化配额 → 绑定默认角色。
     *
     * @param registerDTO 注册参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO registerDTO) {
        ExceptionUtil.throwIf(userMapperService.findByUsername(registerDTO.getUsername()).isPresent(),
                USERNAME_EXISTS);
        ExceptionUtil.throwIf(userMapperService.findByEmail(registerDTO.getEmail()).isPresent(),
                EMAIL_EXISTS);

        String cachedCode = verificationCodeRedis.getVerificationCode(registerDTO.getEmail());
        if (cachedCode == null || !cachedCode.equals(registerDTO.getCode())) {
            log.warn("注册验证码校验失败");
            ExceptionUtil.throwBizException(VERIFICATION_CODE_ERROR);
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapperService.save(user);

        verificationCodeRedis.deleteVerificationCode(registerDTO.getEmail());

        UserCenter userCenter = userCenterConverter.toUserCenter(user, FileConstant.BASE_SIZE, FileConstant.MAX_SIZE);
        userCenterMapperService.save(userCenter);

        addRole(user.getId(), CommonConstants.UserRole.USER_ID);
        log.info("注册成功: userId={}", user.getId());
    }

    /**
     * 发送注册验证码。
     *
     * @param email 目标邮箱
     */
    @Override
    public void sendVerificationCode(@NotBlank @Email String email) {
        if (userMapperService.findByEmail(email).isPresent()) {
            log.warn("验证码发送拒绝: reason=email_exists");
            ExceptionUtil.throwBizException(EMAIL_EXISTS);
        }

        String code = String.valueOf(CODE_ORIGIN + random.nextInt(CODE_BOUND));
        if (!EmailUtil.sendVerificationCode(email, code)) {
            log.error("注册验证码邮件发送失败");
            ExceptionUtil.throwBizException(ErrorCode.EMAIL_SEND_ERROR);
        }

        verificationCodeRedis.saveVerificationCode(email, code);
        log.info("注册验证码发送完成");
    }

    /**
     * 当前用户登出。
     */
    @Override
    public void logout() {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN);
        Long userId = UserContext.getCurrentUserId();
        StpUtil.logout();
        log.info("登出成功: userId={}", userId);
    }

    /**
     * 强制指定用户下线（管理员）。
     *
     * @param userId 目标用户 ID
     */
    @Override
    public void forceLogout(Long userId) {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN, "操作者未登录");
        StpUtil.logout(userId);
        log.info("强制下线完成: targetUserId={}", userId);
    }

    /**
     * 为用户绑定角色。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     */
    @Override
    public void addRole(Long userId, Long roleId) {
        userMapperService.setUserRole(userId, roleId);
        log.info("添加角色完成: userId={}, roleId={}", userId, roleId);
    }

    /**
     * 修改用户角色（管理员）。
     *
     * @param userRole 用户与角色的关联
     */
    @Override
    public void changeUserRole(UserRole userRole) {
        userMapperService.updateUserRole(userRole);
        log.info("用户角色修改完成: userId={}, roleId={}", userRole.getUserId(), userRole.getRoleId());
    }
}
