package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 功能描述：验证码Redis操作类
 * 负责邮箱验证码的存储、获取和删除操作，
 * 通过Redisson的RBucket将验证码存入Redis，默认5分钟过期。
 *
 * @author 飞飞
 * @date 2026/1/20 20:27
 * @version 1.0
 */

@Component
public class VerificationCodeRedis {
    /** Redisson客户端实例，用于操作Redis存储验证码 */
    private final RedissonClient redissonClient;
    /** Redis键前缀，与邮箱拼接组成完整的验证码存储键 */
    private final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 构造函数，通过Spring注入RedissonClient
     *
     * @param redissonClient Redisson客户端实例
     */
    public VerificationCodeRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private String getKey(String email) {
        return VERIFICATION_CODE_KEY_PREFIX + email;
    }

    private RBucket<String> getBucket(String email){
        //根据VERIFICATION_CODE_KEY_PREFIX + email得到操作对象
        RBucket<String> bucket = redissonClient.getBucket(getKey(email));
        return bucket;
    }

    /**
     * 保存邮箱验证码到Redis
     * 以邮箱为键将验证码存入Redis，默认有效期为5分钟。
     *
     * @param email            邮箱地址，作为Redis键的一部分
     * @param verificationCode 验证码字符串
     */
    public void saveVerificationCode(String email, String verificationCode) {
        RBucket<String> bucket = getBucket(email);
        bucket.set(verificationCode, Duration.ofMillis(CommonConstants.Time.FIVE_MINUTES));
    }

    /**
     * 删除指定邮箱的验证码
     * 验证通过或过期后调用，从Redis中移除验证码记录。
     *
     * @param email 邮箱地址
     */
    public void deleteVerificationCode(String email) {
        RBucket<String> bucket = getBucket(email);
        bucket.delete();
    }

    /**
     * 获取指定邮箱的验证码
     * 从Redis中查询并返回邮箱对应的验证码，用于与用户输入的验证码比对。
     *
     * @param email 邮箱地址
     * @return 验证码字符串，如果不存在或已过期则返回null
     */
    public String getVerificationCode(String email) {
        RBucket<String> bucket = getBucket(email);
        return bucket.get();
    }

}
