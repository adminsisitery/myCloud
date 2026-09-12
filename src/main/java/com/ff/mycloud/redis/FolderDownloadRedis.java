package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.FolderDownloadTask;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class FolderDownloadRedis {
    private final RedissonClient redisson;

    public FolderDownloadRedis(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void save(FolderDownloadTask task) {
        redisson.<FolderDownloadTask>getBucket(key(task.getTaskId())).set(task, Duration.ofDays(2));
    }

    public FolderDownloadTask get(String taskId) {
        return redisson.<FolderDownloadTask>getBucket(key(taskId)).get();
    }

    public FolderDownloadTask update(String taskId, Consumer<FolderDownloadTask> updater) {
        RLock lock = redisson.getLock(key(taskId) + ":state-lock");
        lock.lock();
        try {
            FolderDownloadTask task = get(taskId);
            if (task == null) return null;
            updater.accept(task);
            save(task);
            return task;
        } finally {
            lock.unlock();
        }
    }

    public RLock processingLock(String taskId) {
        return redisson.getLock(key(taskId) + ":processing-lock");
    }

    public void delete(String taskId) {
        redisson.getKeys().delete(key(taskId));
    }

    public List<String> scanTaskIds() {
        List<String> result = new ArrayList<>();
        String prefix = CommonConstants.FolderDownloadMQ.REDIS_PREFIX;
        for (String key : redisson.getKeys().getKeysByPattern(prefix + "*")) {
            if (!key.contains(":state-lock") && !key.contains(":processing-lock")) {
                result.add(key.substring(prefix.length()));
            }
        }
        return result;
    }

    private String key(String taskId) {
        return CommonConstants.FolderDownloadMQ.REDIS_PREFIX + taskId;
    }
}
