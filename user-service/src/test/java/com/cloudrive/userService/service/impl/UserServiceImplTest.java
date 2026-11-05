package com.cloudrive.userService.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.constant.FileConstant;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.EmailUtil;
import com.cloudrive.pan_common.util.PasswordUtil;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试。
 * <p>
 * 对照 TEST-CASES.md「user-service 关键 Service 单元测试点」逐条覆盖：
 * login 的四条失败路径 + 成功路径、register 的三道校验与落库、sendVerificationCode 的两条拒绝路径、
 * forceLogout 未登录。
 * </p>
 * <p>
 * 依赖全部 mock：Mapper / Redis / 转换器走 @Mock，PasswordUtil、StpUtil、EmailUtil 这类静态工具走
 * {@link MockedStatic}——真实调用需要 BCrypt 和 Sa-Token 上下文，和被测逻辑无关。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String IP = "unknown";   // 单测没有请求上下文，IpUtil 会回退成 "unknown"

    @Mock UserMapperService userMapperService;
    @Mock UserCenterMapperService userCenterMapperService;
    @Mock UserCenterConverter userCenterConverter;
    @Mock VerificationCodeRedis verificationCodeRedis;
    @Mock LoginFailRedis loginFailRedis;

    @InjectMocks UserServiceImpl service;

    // ------------------------------------------------------------------ 夹具

    private User user(Long id, String encodedPassword, Integer status) {
        User u = new User();
        u.setId(id);
        u.setUsername("tester");
        u.setPassword(encodedPassword);
        u.setEmail("tester@example.com");
        u.setStatus(status);
        return u;
    }

    private LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private RegisterDTO registerDTO(String username, String email, String code) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("Abc123456");
        dto.setEmail(email);
        dto.setCode(code);
        return dto;
    }

    /** 抓业务异常，顺便断言类型。用 catchThrowable + 类型断言，避免踩 AssertJ 重载版本差异。 */
    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    /** 未触发限流/锁定的返回值：[0]=blocked, [1]=failCount, [2]=delay */
    private long[] passThrough() {
        return new long[]{0, 1, 0};
    }

    // ================================================================== login

    @Test
    @DisplayName("login：用户名不存在 → USER_NOT_FOUND(404)，且失败计数记在 userId=-1 上")
    void login_用户名不存在时抛出USER_NOT_FOUND() {
        when(userMapperService.findByUsername("ghost")).thenReturn(Optional.empty());
        when(loginFailRedis.recordAndCheck(-1L, IP)).thenReturn(passThrough());

        BusinessException ex = bizExceptionOf(() -> service.login(loginDTO("ghost", "whatever")));

        assertThat(ex.getCode()).isEqualTo(404);
        assertThat(ex.getMessage()).isEqualTo("用户不存在");
        // 用户名不存在也要计数，但记在 -1 这个占位 ID 上，避免泄露账号是否存在
        verify(loginFailRedis).recordAndCheck(-1L, IP);
    }

    @Test
    @DisplayName("login：密码错误 → INVALID_PASSWORD(401)，并用真实 userId 补记一次失败")
    void login_密码错误时抛出INVALID_PASSWORD并补记账户维度() {
        User exist = user(USER_ID, "encoded", 1);
        when(userMapperService.findByUsername("tester")).thenReturn(Optional.of(exist));
        when(loginFailRedis.recordAndCheck(USER_ID, IP)).thenReturn(passThrough());

        BusinessException ex;
        try (MockedStatic<PasswordUtil> pwd = mockStatic(PasswordUtil.class)) {
            pwd.when(() -> PasswordUtil.matches("wrong", "encoded")).thenReturn(false);
            ex = bizExceptionOf(() -> service.login(loginDTO("tester", "wrong")));
        }

        assertThat(ex.getCode()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("密码错误");
        // 一次进门计数 + 一次密码错误补记，共两次
        verify(loginFailRedis, times(2)).recordAndCheck(USER_ID, IP);
        verify(loginFailRedis, never()).reset(anyLong(), anyString());
    }

    @Test
    @DisplayName("login：账户已被锁定 → ACCOUNT_LOCKED(423)，且不再校验密码")
    void login_账户锁定时直接拒绝() {
        when(userMapperService.findByUsername("tester")).thenReturn(Optional.of(user(USER_ID, "encoded", 1)));
        when(loginFailRedis.recordAndCheck(USER_ID, IP)).thenReturn(new long[]{1, 6, 0});

        BusinessException ex;
        try (MockedStatic<PasswordUtil> pwd = mockStatic(PasswordUtil.class)) {
            ex = bizExceptionOf(() -> service.login(loginDTO("tester", "whatever")));
            // 锁定分支在密码校验之前返回，PasswordUtil 不该被调用
            pwd.verifyNoInteractions();
        }

        assertThat(ex.getCode()).isEqualTo(423);
        assertThat(ex.getMessage()).contains("锁定");
        verify(loginFailRedis, never()).reset(anyLong(), anyString());
    }

    @Test
    @DisplayName("login：账户被禁用 → ACCOUNT_DISABLED(400)")
    void login_账户禁用时抛出ACCOUNT_DISABLED() {
        when(userMapperService.findByUsername("tester")).thenReturn(Optional.of(user(USER_ID, "encoded", 0)));
        when(loginFailRedis.recordAndCheck(USER_ID, IP)).thenReturn(passThrough());

        BusinessException ex;
        try (MockedStatic<PasswordUtil> pwd = mockStatic(PasswordUtil.class)) {
            pwd.when(() -> PasswordUtil.matches("right", "encoded")).thenReturn(true);
            ex = bizExceptionOf(() -> service.login(loginDTO("tester", "right")));
        }

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("账号已被禁用");
    }

    @Test
    @DisplayName("login：成功 → 重置失败计数、签发 token")
    void login_成功时重置计数并返回token() {
        User exist = user(USER_ID, "encoded", 1);
        when(userMapperService.findByUsername("tester")).thenReturn(Optional.of(exist));
        when(loginFailRedis.recordAndCheck(USER_ID, IP)).thenReturn(passThrough());

        String token;
        try (MockedStatic<PasswordUtil> pwd = mockStatic(PasswordUtil.class);
             MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            pwd.when(() -> PasswordUtil.matches("right", "encoded")).thenReturn(true);
            stp.when(() -> StpUtil.getTokenValue()).thenReturn("token-abc");

            token = service.login(loginDTO("tester", "right"));

            stp.verify(() -> StpUtil.login(USER_ID));
        }

        assertThat(token).isEqualTo("token-abc");
        verify(loginFailRedis).reset(USER_ID, IP);
    }

    // ================================================================== register

    @Test
    @DisplayName("register：用户名已存在 → USERNAME_EXISTS(400)，且不查邮箱")
    void register_用户名重复时抛USERNAME_EXISTS() {
        when(userMapperService.findByUsername("dup")).thenReturn(Optional.of(user(USER_ID, "x", 1)));

        BusinessException ex = bizExceptionOf(() -> service.register(registerDTO("dup", "new@example.com", "123456")));

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("用户名已存在");
        verify(userMapperService, never()).findByEmail(anyString());
        verify(userMapperService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register：邮箱已存在 → EMAIL_EXISTS(400)")
    void register_邮箱重复时抛EMAIL_EXISTS() {
        when(userMapperService.findByUsername("fresh")).thenReturn(Optional.empty());
        when(userMapperService.findByEmail("dup@example.com")).thenReturn(Optional.of(user(9L, "x", 1)));

        BusinessException ex = bizExceptionOf(() -> service.register(registerDTO("fresh", "dup@example.com", "123456")));

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("邮箱已被注册");
        verify(userMapperService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("register：验证码不匹配 → VERIFICATION_CODE_ERROR(41005)，不落库")
    void register_验证码错误时抛出VERIFICATION_CODE_ERROR() {
        when(userMapperService.findByUsername("fresh")).thenReturn(Optional.empty());
        when(userMapperService.findByEmail("fresh@example.com")).thenReturn(Optional.empty());
        when(verificationCodeRedis.getVerificationCode("fresh@example.com")).thenReturn("111111");

        BusinessException ex = bizExceptionOf(() -> service.register(registerDTO("fresh", "fresh@example.com", "222222")));

        assertThat(ex.getCode()).isEqualTo(41005);
        assertThat(ex.getMessage()).isEqualTo("验证码错误或已过期");
        verify(userMapperService, never()).save(any(User.class));
        verify(userCenterMapperService, never()).save(any(UserCenter.class));
    }

    @Test
    @DisplayName("register：验证码未发送（Redis 无值）→ 同样按验证码错误处理")
    void register_验证码不存在时抛出VERIFICATION_CODE_ERROR() {
        when(userMapperService.findByUsername("fresh")).thenReturn(Optional.empty());
        when(userMapperService.findByEmail("fresh@example.com")).thenReturn(Optional.empty());
        when(verificationCodeRedis.getVerificationCode("fresh@example.com")).thenReturn(null);

        BusinessException ex = bizExceptionOf(() -> service.register(registerDTO("fresh", "fresh@example.com", "222222")));

        assertThat(ex.getCode()).isEqualTo(41005);
    }

    @Test
    @DisplayName("register：成功 → 写 user、删验证码、建 user_center、绑普通用户角色")
    void register_成功时落库三张表并删除验证码() {
        when(userMapperService.findByUsername("fresh")).thenReturn(Optional.empty());
        when(userMapperService.findByEmail("fresh@example.com")).thenReturn(Optional.empty());
        when(verificationCodeRedis.getVerificationCode("fresh@example.com")).thenReturn("123456");

        UserCenter center = new UserCenter();
        when(userCenterConverter.toUserCenter(any(User.class), eq(FileConstant.BASE_SIZE), eq(FileConstant.MAX_SIZE)))
                .thenReturn(center);

        try (MockedStatic<PasswordUtil> pwd = mockStatic(PasswordUtil.class)) {
            pwd.when(() -> PasswordUtil.encode("Abc123456")).thenReturn("encoded-pwd");

            service.register(registerDTO("fresh", "fresh@example.com", "123456"));
        }

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapperService).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("fresh");
        assertThat(saved.getPassword()).isEqualTo("encoded-pwd");   // 落库的是加密后的密码
        assertThat(saved.getStatus()).isEqualTo(1);

        verify(verificationCodeRedis).deleteVerificationCode("fresh@example.com");
        verify(userCenterMapperService).save(center);
        verify(userMapperService).setUserRole(any(), eq(CommonConstants.UserRole.USER_ID));
    }

    // ================================================================== sendVerificationCode

    @Test
    @DisplayName("sendVerificationCode：邮箱已注册 → EMAIL_EXISTS(400)，不发信不写 Redis")
    void sendVerificationCode_邮箱已注册时拒绝() {
        when(userMapperService.findByEmail("used@example.com")).thenReturn(Optional.of(user(USER_ID, "x", 1)));

        BusinessException ex = bizExceptionOf(() -> service.sendVerificationCode("used@example.com"));

        assertThat(ex.getCode()).isEqualTo(400);
        verifyNoInteractions(verificationCodeRedis);
    }

    @Test
    @DisplayName("sendVerificationCode：邮件发送失败 → EMAIL_SEND_ERROR(41006)，且不写 Redis")
    void sendVerificationCode_发送失败时不写验证码() {
        when(userMapperService.findByEmail("new@example.com")).thenReturn(Optional.empty());

        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            mail.when(() -> EmailUtil.sendVerificationCode(eq("new@example.com"), anyString())).thenReturn(false);

            BusinessException ex = bizExceptionOf(() -> service.sendVerificationCode("new@example.com"));

            assertThat(ex.getCode()).isEqualTo(41006);
        }

        verify(verificationCodeRedis, never()).saveVerificationCode(anyString(), anyString());
    }

    @Test
    @DisplayName("sendVerificationCode：成功 → 写入 6 位验证码")
    void sendVerificationCode_成功时写入6位验证码() {
        when(userMapperService.findByEmail("new@example.com")).thenReturn(Optional.empty());

        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            mail.when(() -> EmailUtil.sendVerificationCode(eq("new@example.com"), anyString())).thenReturn(true);

            service.sendVerificationCode("new@example.com");
        }

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(verificationCodeRedis).saveVerificationCode(eq("new@example.com"), codeCaptor.capture());
        // 100000 ~ 999999，固定 6 位
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    // ================================================================== 登录态相关

    @Test
    @DisplayName("logout：未登录 → USER_NOT_LOGGED_IN(401)")
    void logout_未登录时抛出USER_NOT_LOGGED_IN() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);

            BusinessException ex = bizExceptionOf(() -> service.logout());

            assertThat(ex.getCode()).isEqualTo(401);
            assertThat(ex.getMessage()).isEqualTo("用户未登录");
            stp.verify(StpUtil::logout, never());
        }
    }

    @Test
    @DisplayName("forceLogout：操作者未登录 → USER_NOT_LOGGED_IN(401)")
    void forceLogout_未登录时抛出USER_NOT_LOGGED_IN() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);

            BusinessException ex = bizExceptionOf(() -> service.forceLogout(9L));

            assertThat(ex.getCode()).isEqualTo(401);
            assertThat(ex.getMessage()).isEqualTo("操作者未登录");
            stp.verify(() -> StpUtil.logout(9L), never());
        }
    }

    // ================================================================== 角色

    @Test
    @DisplayName("addRole / changeUserRole：直接委派给 Mapper，不做额外校验")
    void 角色操作直接委派给Mapper() {
        service.addRole(USER_ID, 2L);
        verify(userMapperService).setUserRole(USER_ID, 2L);

        UserRole userRole = new UserRole();
        userRole.setUserId(USER_ID);
        userRole.setRoleId(1L);
        service.changeUserRole(userRole);
        verify(userMapperService).updateUserRole(userRole);
    }
}
