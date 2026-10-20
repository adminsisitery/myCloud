package com.cloudrive.fileTaskService.redis;

import com.cloudrive.fileTaskService.model.entity.DirectUploadTask;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class DirectUploadRedis {
    private static final String PREFIX = "direct:upload:";
    private final RedissonClient redisClient;

    public DirectUploadRedis(RedissonClient redisClient) {
        this.redisClient = redisClient;
    }

    public void save(DirectUploadTask task) {
        // 保留足够长时间供定时任务归还配额，业务过期时间由 expiresAt 判断。
        redisClient.<DirectUploadTask>getBucket(PREFIX + task.getTaskId()).set(task, Duration.ofDays(2));
    }

    public DirectUploadTask get(String taskId) {
        return redisClient.<DirectUploadTask>getBucket(PREFIX + taskId).get();
    }

    public void delete(String taskId) {
        redisClient.getKeys().delete(PREFIX + taskId);
    }

    public RLock getLock(String taskId) {
        return redisClient.getLock(PREFIX + taskId + ":lock");
    }

    public List<String> scanTaskIds() {
        List<String> ids = new ArrayList<>();
        for (String key : redisClient.getKeys().getKeysByPattern(PREFIX + "*")) {
            if (!key.endsWith(":lock")) ids.add(key.substring(PREFIX.length()));
        }
        return ids;
    }
}
