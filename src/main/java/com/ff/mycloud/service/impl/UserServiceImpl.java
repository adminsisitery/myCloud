package com.ff.mycloud.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.FileConstant;
import com.ff.mycloud.common.util.EmailUtil;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.IpUtil;
import com.ff.mycloud.common.util.PasswordUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.UserCenterMapper;
import com.ff.mycloud.model.dto.LoginDTO;
import com.ff.mycloud.model.dto.RegisterDTO;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.model.entity.UserRole;
import com.ff.mycloud.redis.LoginFailRedis;
import com.ff.mycloud.redis.VerificationCodeRedis;
import com.ff.mycloud.service.UserCenterMapperService;
import com.ff.mycloud.service.UserMapperService;
import com.ff.mycloud.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import static com.ff.mycloud.enums.ErrorCode.*;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/11 21:57
 * @version 1.0
 */

@Service
public class UserServiceImpl implements UserService {

    private final UserMapperServiceImpl userMapperServiceImpl;
    Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    UserMapperService userMapperService;
    VerificationCodeRedis verificationCodeRedis;
    UserCenterMapperService userCenterMapperService;
    UserCenterMapper userCenterMapper;
    LoginFailRedis loginFailRedis;

    public UserServiceImpl(VerificationCodeRedis verificationCodeRedis,
                           UserCenterMapper userCenterMapper,
                           UserMapperService userMapperService,
                           UserCenterMapperService userCenterMapperService,
                           UserMapperServiceImpl userMapperServiceImpl,
                           LoginFailRedis loginFailRedis) {
        this.verificationCodeRedis = verificationCodeRedis;
        this.userMapperService = userMapperService;
        this.userCenterMapperService = userCenterMapperService;
        this.userMapperServiceImpl = userMapperServiceImpl;
        this.userCenterMapper = userCenterMapper;
        this.loginFailRedis = loginFailRedis;
    }

    /**
     * 登录
     * 三层防护：@RateLimit 限流 → IP维度计数 → 账户维度渐进退避+硬锁
     */
    @Override
    public String login(LoginDTO loginDTO) {
        logger.info("开始处理登录请求，用户名：{}", loginDTO.getUsername());

        String ip = IpUtil.getClientIp();

        // ① 先查用户是否存在（用于获取 userId 做账户维度计数）
        Optional<User> users = userMapperService.findByUsername(loginDTO.getUsername());
        Long userId = users.map(User::getId).orElse(-1L);

        // ② 检查是否已被锁（双维度 Lua 原子判断）
        long[] status = loginFailRedis.recordAndCheck(userId, ip);
        if (status[0] == 1) {
            logger.warn("登录被拦截：userId={}, ip={}, failCount={}", userId, ip, status[1]);
            ExceptionUtil.throwBizException(ACCOUNT_LOCKED);
        }

        // ③ 用户名不存在：已用 -1 计入 IP 维度，不暴露用户是否存在
        if (!users.isPresent()) {
            logger.warn("登录失败：用户名不存在，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(USER_NOT_FOUND);
        }
        User user = users.get();

        // ④ 验证密码：失败时用真实 userId 取代 -1 再记一次 用户存在的情况
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            loginFailRedis.recordAndCheck(user.getId(), ip); // 覆盖 -1 的计数
            logger.warn("登录失败：密码错误，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(INVALID_PASSWORD);
        }

        // ⑤ 验证状态
        if (user.getStatus() != 1) {
            logger.warn("登录失败：账号已被禁用，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(ACCOUNT_DISABLED);
        }

        // ⑥ 登录成功，清除失败记录
        loginFailRedis.reset(user.getId(), ip);

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        logger.info("登录成功，用户名：{}，token：{} id{}", loginDTO.getUsername(), token, UserContext.getCurrentUserId());
        return token;
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        //查看用户是否注册
        ExceptionUtil.throwIf(userMapperService.findByUsername(registerDTO.getUsername()).isPresent(),
                USERNAME_EXISTS);

        //查看邮箱是否注册
        ExceptionUtil.throwIf(userMapperService.findByEmail(registerDTO.getEmail()).isPresent(),
                EMAIL_EXISTS);
        //验证码验证
        String verificationCode = verificationCodeRedis.getVerificationCode(registerDTO.getEmail());
        if (!verificationCode.equals(registerDTO.getCode())) {
            logger.warn("验证码不正确{}", registerDTO.getCode());
            ExceptionUtil.throwBizException(VERIFICATION_CODE_ERROR);
        }

        //创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapperService.save(user);
        //redis删除验证码
        verificationCodeRedis.deleteVerificationCode(user.getEmail());

        //创建完用户保存信息到userCenter表同时
        UserCenter userCenter = userCenterMapper.UserToUserCenter(user, FileConstant.BASE_SIZE,FileConstant.MAX_SIZE);
        userCenterMapperService.save(userCenter);

        //为用户绑定角色
        addRole(user.getId(), CommonConstants.UserRole.USER_ID);
    }
    @Override
    public void sendVerificationCode(@NotBlank @Email String email) {
        //检查邮箱是否注册
        Optional<User> userByEmail = userMapperService.findByEmail(email);
        if (userByEmail.isPresent()) {
            logger.warn("邮箱已被注册{}", email);
            ExceptionUtil.throwBizException(EMAIL_EXISTS);
        }

        //生成6位数字验证码
        int i = 10000 + new Random().nextInt(900000);
        String code = String.valueOf(i);

        //发送验证码
        boolean success = EmailUtil.sendVerificationCode(email, code);
        System.out.println(code);
        if (!success) {
            ExceptionUtil.throwBizException(ErrorCode.EMAIL_SEND_ERROR);
        }

        //保存到redis
        verificationCodeRedis.saveVerificationCode(email, code);
    }
    @Override
    public void logout() {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN);
        StpUtil.logout();
    }
    @Override
    public void forceLogout(Long userId) {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN, "操作者未登录");
        // 强制指定用户下线
        StpUtil.logout(userId);
    }

    @Override
    public void addRole(Long userId, Long roleId) {
        userMapperService.SetUserRole(userId,roleId);
    }

    @Override
    public void changeUserRole(UserRole userRole) {
        userMapperService.updataUserRole(userRole);
    }
}
