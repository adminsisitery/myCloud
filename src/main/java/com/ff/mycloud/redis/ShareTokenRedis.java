package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.ShareConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/6 12:35
 * @version 1.0
 */

@Component
public class ShareTokenRedis {
    private final RedissonClient redissonClient;

    public ShareTokenRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private String getKey(String shareCode) {
        return ShareConstants.Redis.TOKEN_PREFIX + shareCode;
    }

    private RBucket<String> getBucket(String shareCode) {
        return redissonClient.getBucket(getKey(shareCode));
    }

    public boolean validateToken(String shareCode, String token) {
        RBucket<String> bucket = getBucket(shareCode);
        String stoken = bucket.get();

        if (stoken == null || !stoken.equals(token)) {
            return false;
        }
        bucket.expire(Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return true;
    }

    public String generateAndStoreToken(String shareCode) {

        String token = UUID.randomUUID().toString().replace("-", "");
        getBucket(shareCode).set(token, Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return token;
    }

    /**
     * 删除分享令牌
     * @param shareCode 分享码
     */
    public void deleteToken(String shareCode) {
        getBucket(shareCode).delete();
    }
}
