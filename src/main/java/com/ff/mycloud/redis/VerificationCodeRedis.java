package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @description: TODO
 * @author 飞飞
 * @date 2026/1/20 20:27
 * @version 1.0
 */

@Component
public class VerificationCodeRedis {
    private final RedissonClient redissonClient;
    private final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";
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

    public void saveVerificationCode(String email, String verificationCode) {
        RBucket<String> bucket = getBucket(email);
        bucket.set(verificationCode, Duration.ofMillis(CommonConstants.Time.FIVE_MINUTES));
    }

    public void deleteVerificationCode(String email) {
        RBucket<String> bucket = getBucket(email);
        bucket.delete();
    }

    public String getVerificationCode(String email) {
        RBucket<String> bucket = getBucket(email);
        return bucket.get();
    }

}
