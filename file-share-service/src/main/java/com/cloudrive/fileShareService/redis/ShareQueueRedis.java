package com.cloudrive.fileShareService.redis;

import com.cloudrive.fileShareService.common.ShareConstants;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 分享过期延迟队列 Redis 操作类
 * <p>
 * 基于 Redisson RDelayedQueue：创建分享时把记录 ID 按过期时间放入延迟队列，
 * 到期后由 {@link com.cloudrive.fileShareService.task.ShareExpireConsumer} 消费并标记失效。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class ShareQueueRedis {

    private final RedissonClient redissonClient;

    public ShareQueueRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 将分享记录 ID 加入延迟队列
     *
     * @param id         分享记录 ID
     * @param expireTime 分享过期时间
     */
    public void addToDelayedQueue(Long id, LocalDateTime expireTime) {
        RBlockingQueue<Long> blockingQueue =
                redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue =
                redissonClient.getDelayedQueue(blockingQueue);
        long delay = Duration.between(LocalDateTime.now(), expireTime).toSeconds();
        delayedQueue.offer(id, delay, TimeUnit.SECONDS);
    }

    /**
     * 从延迟队列中移除指定分享记录 ID（取消分享时调用）
     *
     * @param shareId 分享记录 ID
     */
    public void removeFromDelayedQueue(Long shareId) {
        RBlockingQueue<Long> blockingQueue =
                redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue =
                redissonClient.getDelayedQueue(blockingQueue);
        delayedQueue.remove(shareId);
    }
}
