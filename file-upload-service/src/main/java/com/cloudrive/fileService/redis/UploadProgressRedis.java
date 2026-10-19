package com.cloudrive.fileService.redis;

import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.fileService.model.entity.UploadTask;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 上传进度 Redis 操作
 * <p>
 * 基于Redisson Bucket实现上传任务进度的存储与更新。
 * 使用分布式锁保证并发更新时的数据一致性。
 * 支持创建、查询、更新、扫描、删除上传任务，TTL默认1小时。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Component
public class UploadProgressRedis {

    /** Redis Key前缀 */
    public static final String UPLOAD_PROGRESS_PREFIX = "upload_progress:";

    /** 任务更新锁的 Key 后缀（与任务同前缀，扫描时必须排除） */
    public static final String KEY_SUFFIX_LOCK = ":lock";

    private final RedissonClient redisson;

    /**
     * 构造函数
     *
     * @param redisson Redisson客户端实例
     */
    public UploadProgressRedis(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 创建上传任务并存入Redis
     *
     * @param task 上传任务对象
     */
    public void createTask(UploadTask task) {
        getBucket(task.getId()).set(task, taskTtl());
    }

    /**
     * 从Redis获取上传任务
     *
     * @param taskId 任务ID
     * @return 上传任务对象，不存在返回null
     */
    public UploadTask getTask(String taskId) {
        return getBucket(taskId).get();
    }

    /**
     * 保存/更新上传任务到Redis
     *
     * @param task 上传任务对象
     */
    public void saveTask(UploadTask task) {
        getBucket(task.getId()).set(task, taskTtl());
    }

    /**
     * 带分布式锁的任务更新操作
     * <p>
     * 加锁后读取任务 -> 执行更新函数 -> 保存回Redis -> 释放锁。
     * 保证并发场景下任务状态的原子更新。
     * </p>
     *
     * @param taskId  任务ID
     * @param updater 更新函数，接收当前任务对象并返回自定义结果
     * @param <T>     更新函数的返回值类型
     * @return 更新函数的返回值，任务不存在返回null
     */
    public <T> T updateTask(String taskId, Function<UploadTask, T> updater) {
        RLock lock = redisson.getLock(getKey(taskId) + KEY_SUFFIX_LOCK);
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

    /**
     * 获取任务对应的Redis Bucket
     *
     * @param taskId 任务ID
     * @return RBucket实例
     */
    private RBucket<UploadTask> getBucket(String taskId) {
        return redisson.getBucket(getKey(taskId));
    }

    /**
     * 组装完整的Redis Key
     *
     * @param taskId 任务ID
     * @return 完整Redis Key，格式: upload_progress:{taskId}
     */
    private String getKey(String taskId) {
        return UPLOAD_PROGRESS_PREFIX + taskId;
    }

    /**
     * 扫描所有上传任务ID
     * <p>基于Redis SCAN命令，不阻塞Redis服务。</p>
     * <p>注意：{@code updateTask} 的分布式锁 key 与任务同前缀（{@code upload_progress:{taskId}:lock}），
     * 会被 {@code upload_progress:*} 的模式一并命中。锁 key 是 Hash 类型，对它执行
     * {@code RBucket.get()}（即 GET）会因类型不符报错，足以让整个扫描任务中断，
     * 所以在返回前必须把这些 key 排除掉。</p>
     *
     * @return 所有上传任务ID列表
     */
    public List<String> scanAllTaskIds() {
        List<String> taskIds = new ArrayList<>();
        for (String key : redisson.getKeys().getKeysByPattern(UPLOAD_PROGRESS_PREFIX + "*")) {
            String taskId = key.substring(UPLOAD_PROGRESS_PREFIX.length());
            if (taskId.endsWith(KEY_SUFFIX_LOCK)) {
                continue;
            }
            taskIds.add(taskId);
        }
        return taskIds;
    }

    /**
     * 删除上传任务
     * <p>用于超时扫描清理或任务取消后释放Redis空间。</p>
     *
     * @param taskId 任务ID
     */
    public void deleteTask(String taskId) {
        redisson.getKeys().delete(getKey(taskId));
    }

    /**
     * 获取任务数据在Redis中的存活时间
     *
     * @return 固定1小时的TTL
     */
    private Duration taskTtl() {
        return Duration.ofMillis(CommonConstants.Time.ONE_HOUR);
    }
}
