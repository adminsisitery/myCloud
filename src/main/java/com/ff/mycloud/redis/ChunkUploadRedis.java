package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.ChunkTask;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChunkUploadRedis {

    /** 分片任务 TTL：7天 */
    private static final long TASK_TTL_DAYS = 7;

    private final RedissonClient redisClient;

    public ChunkUploadRedis(RedissonClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * 保存分片上传任务到Redis
     */
    public void saveTask(ChunkTask chunkTask) {
        RBucket<ChunkTask> bucket = redisClient.getBucket(CommonConstants.ChunkUpload.KEY_PREFIX + chunkTask.getUploadId());
        bucket.set(chunkTask, Duration.ofDays(TASK_TTL_DAYS));
    }

    /**
     * 获取分片上传任务
     */
    public ChunkTask getTask(String uploadId) {
        RBucket<ChunkTask> bucket = redisClient.getBucket(CommonConstants.ChunkUpload.KEY_PREFIX + uploadId);
        return bucket.get();
    }

    /**
     * 删除分片上传任务
     */
    public void deleteTask(String uploadId) {
        redisClient.getKeys().delete(CommonConstants.ChunkUpload.KEY_PREFIX + uploadId);
    }

}
