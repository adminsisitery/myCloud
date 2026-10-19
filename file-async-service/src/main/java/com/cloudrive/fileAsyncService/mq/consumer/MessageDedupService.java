package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQ 消费幂等去重（Redis SETNX，消息 ID 24 小时窗口）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDedupService {

    private final RedissonClient redissonClient;

    /**
     * 尝试消费一条消息
     *
     * @param msgId 生产端生成的消息 ID
     * @return true 表示首次消费，可以继续处理；false 表示重复消息，应跳过
     */
    public boolean tryConsume(String msgId) {
        if (msgId == null || msgId.isBlank()) {
            return true;
        }
        String key = MqConstants.Dedup.KEY_PREFIX + msgId;
        Boolean first = redissonClient.getBucket(key)
                .setIfAbsent("1", Duration.ofSeconds(MqConstants.Dedup.TTL_SECONDS));
        return Boolean.TRUE.equals(first);
    }
}
