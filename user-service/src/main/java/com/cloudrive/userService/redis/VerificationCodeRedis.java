package com.cloudrive.userService.redis;

import com.cloudrive.pan_common.constant.CommonConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 邮箱验证码的 Redis 存取。
 * <p>key 形如 {@code verification_code:{email}}，默认 5 分钟过期。</p>
 *
 * @author 飞飞
 * @date 2026/1/20
 */
@Component
public class VerificationCodeRedis {

    private static final String KEY_PREFIX = "verification_code:";

    private final RedissonClient redissonClient;

    public VerificationCodeRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private RBucket<String> bucket(String email) {
        return redissonClient.getBucket(KEY_PREFIX + email);
    }

    /**
     * 保存验证码，5 分钟后自动过期。
     *
     * @param email 目标邮箱
     * @param code  验证码
     */
    public void saveVerificationCode(String email, String code) {
        bucket(email).set(code, Duration.ofMillis(CommonConstants.Time.FIVE_MINUTES));
    }

    /**
     * 读取验证码。
     *
     * @param email 目标邮箱
     * @return 验证码，不存在或已过期时返回 {@code null}
     */
    public String getVerificationCode(String email) {
        return bucket(email).get();
    }

    /**
     * 删除验证码，校验通过后调用，防止重复使用。
     *
     * @param email 目标邮箱
     */
    public void deleteVerificationCode(String email) {
        bucket(email).delete();
    }
}
