package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.ShareConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 功能描述：分享令牌Redis操作类
 * 负责分享链接的Token生成、存储、验证和删除，
 * 使用UUID生成唯一令牌，通过Redisson的RBucket存储到Redis中。
 *
 * @author 飞飞
 * @date 2026/3/6 12:35
 * @version 1.0
 */

@Component
public class ShareTokenRedis {
    /** Redisson客户端实例，用于操作Redis存储分享令牌 */
    private final RedissonClient redissonClient;

    /**
     * 构造函数，通过Spring注入RedissonClient
     *
     * @param redissonClient Redisson客户端实例
     */
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
     * 从Redis中获取存储的Token并与传入Token进行比较，
     * 验证通过后刷新Token的过期时间。
     *
     * @param shareCode 分享码
     * @param token     待验证的令牌
     * @return true表示令牌有效，false表示令牌无效或不存在
     */
    public boolean validateToken(String shareCode, String token) {
        RBucket<String> bucket = getBucket(shareCode);
        String stoken = bucket.get();

        if (stoken == null || !stoken.equals(token)) {
            return false;
        }
        bucket.expire(Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return true;
    }

    /**
     * 生成并存储分享令牌
     * 使用UUID生成唯一Token（去除横线），存入Redis并设置过期时间。
     *
     * @param shareCode 分享码，作为Redis键的一部分
     * @return 生成的令牌字符串
     */
    public String generateAndStoreToken(String shareCode) {

        String token = UUID.randomUUID().toString().replace("-", "");
        getBucket(shareCode).set(token, Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return token;
    }

    /**
     * 删除分享令牌
     * 从Redis中移除指定分享码对应的Token记录。
     *
     * @param shareCode 分享码
     */
    public void deleteToken(String shareCode) {
        getBucket(shareCode).delete();
    }
}
