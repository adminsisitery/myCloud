package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQ 消息去重服务
 * 基于 Redis SETNX 实现消费者幂等
 *
 * 原理：每条消息带唯一 msgId，消费前执行 SETNX
 *   SETNX 返回 true  → 首次消费，正常处理
 *   SETNX 返回 false → 已消费过（MQ 重复投递），跳过
 *
 * TTL 设为 24 小时，超过此窗口的消息视为新消息
 * （MQ 重试通常在分钟级，24h 绰绰有余）
 */
@Component
public class MessageDedupService {

    private final RedissonClient redisClient;

    public MessageDedupService(RedissonClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * 尝试标记消息为"已消费"
     *
     * @param msgId 消息唯一ID
     * @return true=首次消费（需处理），false=重复消息（跳过）
     */
    public boolean tryConsume(String msgId) {
        if (msgId == null || msgId.isEmpty()) {
            // 兼容旧消息或无 msgId 的消息 — 直接放行，不丢数据
            return true;
        }
        String key = CommonConstants.Dedup.KEY_PREFIX + msgId;
        return redisClient.getBucket(key)
                .setIfAbsent("1", Duration.ofSeconds(CommonConstants.Dedup.TTL_SECONDS));
    }
}
