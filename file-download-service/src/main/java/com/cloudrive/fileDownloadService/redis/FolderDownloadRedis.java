package com.cloudrive.fileDownloadService.redis;

import com.cloudrive.fileDownloadService.common.FolderDownloadConstants;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件夹打包任务 Redis 存储
 * <p>
 * 与单体、file-async-service 共享同一批 key（folder:download:{taskId}）：
 * 本服务负责创建/查询/取消，异步服务负责读取后打包并回写状态。
 * 任务对象使用 pan-common 中的共享类，保证跨服务序列化兼容。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class FolderDownloadRedis {

    /** 任务记录 TTL：2 天（成功压缩包 24 小时过期，留足缓冲） */
    private static final Duration TASK_TTL = Duration.ofDays(2);

    private final RedissonClient redisson;

    public FolderDownloadRedis(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 保存任务
     *
     * @param task 任务对象
     */
    public void save(FolderDownloadTask task) {
        redisson.<FolderDownloadTask>getBucket(key(task.getTaskId())).set(task, TASK_TTL);
    }

    /**
     * 读取任务
     *
     * @param taskId 任务ID
     * @return 任务对象，不存在返回 null
     */
    public FolderDownloadTask get(String taskId) {
        return redisson.<FolderDownloadTask>getBucket(key(taskId)).get();
    }

    /**
     * 原子更新任务（带状态锁，避免与异步消费者并发覆盖）
     *
     * @param taskId  任务ID
     * @param updater 更新逻辑
     * @return 更新后的任务，任务不存在返回 null
     */
    public FolderDownloadTask update(String taskId, Consumer<FolderDownloadTask> updater) {
        RLock lock = redisson.getLock(key(taskId) + ":state-lock");
        lock.lock();
        try {
            FolderDownloadTask task = get(taskId);
            if (task == null) {
                return null;
            }
            updater.accept(task);
            save(task);
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除任务
     *
     * @param taskId 任务ID
     */
    public void delete(String taskId) {
        redisson.getKeys().delete(key(taskId));
    }

    /**
     * 扫描全部任务ID（排除锁键）
     *
     * @return 任务ID列表
     */
    public List<String> scanTaskIds() {
        List<String> result = new ArrayList<>();
        String prefix = FolderDownloadConstants.REDIS_PREFIX;
        for (String key : redisson.getKeys().getKeysByPattern(prefix + "*")) {
            if (!key.contains(":state-lock") && !key.contains(":processing-lock")) {
                result.add(key.substring(prefix.length()));
            }
        }
        return result;
    }

    private String key(String taskId) {
        return FolderDownloadConstants.REDIS_PREFIX + taskId;
    }
}
