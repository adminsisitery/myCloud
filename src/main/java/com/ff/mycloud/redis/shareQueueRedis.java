package com.ff.mycloud.redis;


import com.ff.mycloud.common.constant.ShareConstants;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 功能描述：分享延迟队列Redis操作类
 * 基于Redisson的RDelayedQueue实现分享链接的延迟过期处理，
 * 将分享ID加入延迟队列，到期后由消费者自动清理过期分享。
 *
 * @author 飞飞
 * @date 2026/3/5 15:44
 * @version 1.0
 */

@Component
public class shareQueueRedis {

    /** Redisson客户端实例，用于操作Redis延迟队列 */
    RedissonClient redissonClient;

    /**
     * 构造函数，通过Spring注入RedissonClient
     *
     * @param redissonClient Redisson客户端实例
     */
    public shareQueueRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 将分享ID加入延迟队列，在指定过期时间后由消费者处理
     * 计算当前时间与过期时间的差值作为延迟秒数，将ID加入Redisson延迟队列
     *
     * @param id         分享记录ID
     * @param expireTime 分享过期时间
     */
    public void addToDelayedQueue(Long id, LocalDateTime expireTime){

        RBlockingQueue<Long> blockingQueue =
                redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);

        RDelayedQueue<Long> delayedQueue =
                redissonClient.getDelayedQueue(blockingQueue);

        long delay = Duration.between(LocalDateTime.now(), expireTime).toSeconds();

        delayedQueue.offer(id, delay, TimeUnit.SECONDS);
    }
    /**
     * 从延迟队列中移除指定的分享ID
     * 当分享被手动取消或提前删除时调用，避免到期后重复处理
     *
     * @param shareId 分享记录ID
     */
    public void removeFromDelayedQueue(Long shareId) {
        RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        delayedQueue.remove(shareId);
    }
}
