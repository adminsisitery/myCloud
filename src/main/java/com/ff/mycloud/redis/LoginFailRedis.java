package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录防爆破 Redis 操作
 * 双维度（账户 + IP）+ Lua 原子计数 + 渐进退避
 */
@Component
public class LoginFailRedis {

    private final RedissonClient redisClient;

    public LoginFailRedis(RedissonClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * Lua 脚本：原子执行 INCR + TTL + 阈值判断
     * 返回值: [blocked, totalCount, waitSeconds]
     *   blocked=1 被拦截, blocked=0 放行
     */
    public static final String LUA_SCRIPT = """
            local account_key = KEYS[1]
            local ip_key = KEYS[2]
            local account_max = tonumber(ARGV[1])
            local ip_max = tonumber(ARGV[2])
            local account_ttl = tonumber(ARGV[3])
            local ip_ttl = tonumber(ARGV[4])

            local account_count = redis.call('INCR', account_key)
            redis.call('EXPIRE', account_key, account_ttl)
            local ip_count = redis.call('INCR', ip_key)
            redis.call('EXPIRE', ip_key, ip_ttl)

            -- 账户维度：超过阈值直接锁
            if account_count > account_max then
                return {1, account_count, 0}
            end

            -- IP 维度：超过阈值直接锁
            if ip_count > ip_max then
                return {1, ip_count, 0}
            end

            -- 账户渐进退避（前4次不锁，只递增等待时间）
            local delay = 0
            if account_count == 2 then delay = 2
            elseif account_count == 3 then delay = 5
            elseif account_count == 4 then delay = 10
            end
            return {0, account_count, delay}
            """;

    /**
     * 记录一次登录失败，并返回是否被拦截
     *
     * @param userId 用户ID
     * @param ip     客户端IP
     * @return long[3]: [0]=blocked(1拦截/0放行), [1]=失败次数, [2]=建议等待秒数
     */
    public long[] recordAndCheck(Long userId, String ip) {
        List<Object> result = redisClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                LUA_SCRIPT,
                RScript.ReturnType.MULTI,
                List.of(
                        CommonConstants.LoginFail.ACCOUNT_KEY_PREFIX + userId,
                        CommonConstants.LoginFail.IP_KEY_PREFIX + ip
                ),
                CommonConstants.LoginFail.ACCOUNT_MAX_FAIL,
                CommonConstants.LoginFail.IP_MAX_FAIL,
                CommonConstants.LoginFail.ACCOUNT_LOCK_SECONDS,
                CommonConstants.LoginFail.IP_WINDOW_SECONDS
        );
        return new long[]{
                ((Number) result.get(0)).longValue(),
                ((Number) result.get(1)).longValue(),
                ((Number) result.get(2)).longValue()
        };
    }

    /**
     * 登录成功后清除该用户和 IP 的失败记录
     */
    public void reset(Long userId, String ip) {
        redisClient.getKeys().delete(
                CommonConstants.LoginFail.ACCOUNT_KEY_PREFIX + userId,
                CommonConstants.LoginFail.IP_KEY_PREFIX + ip
        );
    }
}
