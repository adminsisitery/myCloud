package com.ff.mycloud.redis;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.UploadTask;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

@Component
public class UploadProgressRedis {

    public static final String UPLOAD_PROGRESS_PREFIX = "upload_progress:";

    private final RedissonClient redisson;

    public UploadProgressRedis(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void createTask(UploadTask task) {
        getBucket(task.getId()).set(task, taskTtl());

    }

    public UploadTask getTask(String taskId) {
        return getBucket(taskId).get();
    }

    public void saveTask(UploadTask task) {
        getBucket(task.getId()).set(task, taskTtl());
    }

    public <T> T updateTask(String taskId, Function<UploadTask, T> updater) {
        RLock lock = redisson.getLock(getKey(taskId) + ":lock");
        lock.lock();
        try {
            UploadTask task = getTask(taskId);
            if (task == null) {
                return null;
            }
            T result = updater.apply(task);
            saveTask(task);
            return result;
        } finally {
            lock.unlock();
        }
    }

    private RBucket<UploadTask> getBucket(String taskId) {
        return redisson.getBucket(getKey(taskId));
    }

    private String getKey(String taskId) {
        return UPLOAD_PROGRESS_PREFIX + taskId;
    }

    /**
     * 扫描所有上传任务 ID（基于 SCAN，不阻塞 Redis）
     */
    public List<String> scanAllTaskIds() {
        List<String> taskIds = new ArrayList<>();
        for (String key : redisson.getKeys().getKeysByPattern(UPLOAD_PROGRESS_PREFIX + "*")) {
            taskIds.add(key.substring(UPLOAD_PROGRESS_PREFIX.length()));
        }
        return taskIds;
    }

    /**
     * 删除任务（超时扫描清理用）
     */
    public void deleteTask(String taskId) {
        redisson.getKeys().delete(getKey(taskId));
    }

    private Duration taskTtl() {
        return Duration.ofMillis(CommonConstants.Time.ONE_HOUR);
    }
}
