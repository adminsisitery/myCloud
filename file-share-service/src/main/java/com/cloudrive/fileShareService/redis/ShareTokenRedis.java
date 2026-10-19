package com.cloudrive.fileShareService.redis;

import com.cloudrive.fileShareService.common.ShareConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 分享令牌 Redis 操作类
 * <p>
 * 负责分享访问 Token 的生成、存储、验证与删除：
 * Token 使用 UUID 生成，通过 Redisson RBucket 存入 Redis 并设置 24 小时过期。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
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

    /**
     * 验证分享令牌是否有效
     * <p>从 Redis 取出 Token 比对，验证通过后刷新过期时间。</p>
     *
     * @param shareCode 分享码
     * @param token     待验证令牌
     * @return true 表示有效
     */
    public boolean validateToken(String shareCode, String token) {
        RBucket<String> bucket = getBucket(shareCode);
        String storedToken = bucket.get();
        if (storedToken == null || !storedToken.equals(token)) {
            return false;
        }
        bucket.expire(Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return true;
    }

    /**
     * 生成并存储分享令牌
     *
     * @param shareCode 分享码
     * @return 新生成的令牌
     */
    public String generateAndStoreToken(String shareCode) {
        String token = UUID.randomUUID().toString().replace("-", "");
        getBucket(shareCode).set(token, Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return token;
    }

    /**
     * 删除分享令牌
     *
     * @param shareCode 分享码
     */
    public void deleteToken(String shareCode) {
        getBucket(shareCode).delete();
    }
}
