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
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/5 15:44
 * @version 1.0
 */

@Component
public class shareQueueRedis {

    RedissonClient redissonClient;

    public shareQueueRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void addToDelayedQueue(Long id, LocalDateTime expireTime){

        RBlockingQueue<Long> blockingQueue =
                redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);

        RDelayedQueue<Long> delayedQueue =
                redissonClient.getDelayedQueue(blockingQueue);

        long delay = Duration.between(LocalDateTime.now(), expireTime).toSeconds();

        delayedQueue.offer(id, delay, TimeUnit.SECONDS);
    }
    public void removeFromDelayedQueue(Long shareId) {
        RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        delayedQueue.remove(shareId);
    }
}
