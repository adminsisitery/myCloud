package com.ff.mycloud.ai.cache;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * AI 响应缓存 —— 基于 Redisson，相同用户+相同问题+相同模型在 TTL 内直接返回缓存结果，
 * 减少重复 LLM API 调用成本。
 */
@Service
public class AICacheService {

    private static final Logger log = LoggerFactory.getLogger(AICacheService.class);
    private static final String KEY_PREFIX = "ai:cache:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final RedissonClient redisson;

    public AICacheService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /** 计算缓存 key：ai:cache:{userId}:{messageHash}:{modelName} */
    public String cacheKey(Long userId, String message, String model) {
        return KEY_PREFIX + userId + ":" + sha256(message) + ":" + (model != null ? model : "default");
    }

    /** 获取缓存 */
    public String get(Long userId, String message, String model) {
        RBucket<String> bucket = redisson.getBucket(cacheKey(userId, message, model));
        String cached = bucket.get();
        if (cached != null) {
            log.info("[AI Cache] HIT userId={}, model={}, len={}", userId, model, cached.length());
        }
        return cached;
    }

    /** 存入缓存 */
    public void put(Long userId, String message, String model, String response) {
        if (response == null || response.length() < 10) return; // 太短的错误回复不缓存
        RBucket<String> bucket = redisson.getBucket(cacheKey(userId, message, model));
        bucket.set(response, TTL);
        log.info("[AI Cache] PUT userId={}, model={}, len={}", userId, model, response.length());
    }

    /** 清除用户所有缓存 */
    public void evictUser(Long userId) {
        redisson.getKeys().deleteByPattern(KEY_PREFIX + userId + ":*");
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
