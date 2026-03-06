package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.UploadTask;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/3 13:56
 * @version 1.0
 */

@Component
public class UploadProgressRedis{
    private final RedissonClient redisson;
    public static final String UPLOAD_PROGRESS_PREFIX = "upload_progress:";

    public UploadProgressRedis(RedissonClient redisson) {
        this.redisson = redisson;
    }

    private String getKey(String key){
        return UPLOAD_PROGRESS_PREFIX + key;
    }

    private RBucket<UploadTask> getBucket(String taskId){return redisson.getBucket(getKey(taskId));}

    public void createTask(UploadTask task){
        redisson.getBucket(getKey(task.getId())).set(task, Duration.ofMillis(CommonConstants.Time.ONE_HOUR));
    }

    public UploadTask getTask(String taskId){
        RBucket<UploadTask> bucket = getBucket(taskId);
        return bucket.get();
    }

    public void saveTask( UploadTask task){
        RBucket<UploadTask> bucket = getBucket(task.getId());
        bucket.set(task, Duration.ofMillis(CommonConstants.Time.ONE_HOUR));
    }
}
