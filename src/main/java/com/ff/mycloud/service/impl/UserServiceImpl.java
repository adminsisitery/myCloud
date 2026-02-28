package com.ff.mycloud.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ff.mycloud.common.util.EmailUtil;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.PasswordUtil;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.dto.LoginDTO;
import com.ff.mycloud.model.dto.RegisterDTO;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.redis.VerificationCodeRedis;
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

    Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    UserMapperService userMapperService;
    VerificationCodeRedis verificationCodeRedis;

    public UserServiceImpl(VerificationCodeRedis verificationCodeRedis,UserMapperService userMapperService) {
        this.verificationCodeRedis = verificationCodeRedis;
        this.userMapperService = userMapperService;
    }

    @Override
    public String login(LoginDTO loginDTO) {
        logger.info("开始处理登录请求，用户名：{}", loginDTO.getUsername());
//        Optional<User> users = userRepository.findByUsername(loginDTO.getUsername());
        Optional<User> users = userMapperService.findByUsername(loginDTO.getUsername());
        //查看是否有当前用户
        if (!users.isPresent()) {
            logger.warn("登录失败：用户名不存在，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(USER_NOT_FOUND);
        }
        User user = users.get();
        String password = user.getPassword();
        //验证密码
        if (!PasswordUtil.matches(loginDTO.getPassword(),password)) {
            logger.warn("登录失败：密码错误，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(INVALID_PASSWORD);
        }
        //验证status
        if (user.getStatus()!=1){
            logger.warn("登录失败：账号已被禁用，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(ACCOUNT_DISABLED);
        }

        //登录
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        logger.info("登录成功，用户名：{}，token：{}", loginDTO.getUsername(), token);
        //返回token
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
        if (!success) {
            ExceptionUtil.throwBizException(ErrorCode.EMAIL_SEND_ERROR);
        }

        //保存到redis
        verificationCodeRedis.saveVerificationCode(email, code);
    }
}
