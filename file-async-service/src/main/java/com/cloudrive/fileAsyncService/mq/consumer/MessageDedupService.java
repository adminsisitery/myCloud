package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQ 消费幂等缓存（Redis），只作快速路径，不承担强一致。
 * <p>
 * 语义是「处理成功后标记」，不是「处理前占位」。占位式去重一旦后续落库失败，
 * 消息重投时会被误判为重复而直接丢弃，等于丢数据；真正兜底的是
 * {@code operation_log.uk_msg_id} 唯一索引，缓存只用来省掉重复的 DB 冲突检测。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDedupService {

    private final RedissonClient redissonClient;

    /**
     * 是否已经成功消费过。只读判断，不占位，查不到也不会妨碍后续重试。
     *
     * @param msgId 生产端生成的消息 ID
     */
    public boolean isConsumed(String msgId) {
        if (msgId == null || msgId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redissonClient.getBucket(key(msgId)).isExists());
    }

    /**
     * 落库成功后标记，供后续重投短路。
     *
     * @param msgId 生产端生成的消息 ID
     */
    public void markConsumed(String msgId) {
        if (msgId == null || msgId.isBlank()) {
            return;
        }
        redissonClient.getBucket(key(msgId)).set("1", Duration.ofSeconds(MqConstants.Dedup.TTL_SECONDS));
    }

    private String key(String msgId) {
        return MqConstants.Dedup.KEY_PREFIX + msgId;
    }
}
