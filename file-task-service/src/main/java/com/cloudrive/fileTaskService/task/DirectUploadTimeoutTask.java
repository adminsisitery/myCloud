package com.cloudrive.fileTaskService.task;

import com.cloudrive.fileTaskService.client.UserSpaceClient;
import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.model.entity.DirectUploadTask;
import com.cloudrive.fileTaskService.redis.DirectUploadRedis;
import com.cloudrive.fileTaskService.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 直传超时清理
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.task.DirectUploadTimeoutTask} 与
 * file-upload-service 的 {@code DirectUploadServiceImpl#cleanupExpired}：
 * 直传任务超过 expiresAt 未完成时，删除 OSS 临时对象、归还预占空间、清理 Redis 任务。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Slf4j
@Component
public class DirectUploadTimeoutTask {

    private final RedissonClient redissonClient;
    private final DirectUploadRedis directUploadRedis;
    private final UserSpaceClient userSpaceClient;
    private final StorageService storageService;

    public DirectUploadTimeoutTask(RedissonClient redissonClient,
                                   DirectUploadRedis directUploadRedis,
                                   UserSpaceClient userSpaceClient,
                                   StorageService storageService) {
        this.redissonClient = redissonClient;
        this.directUploadRedis = directUploadRedis;
        this.userSpaceClient = userSpaceClient;
        this.storageService = storageService;
    }

    /**
     * 扫描过期直传任务（每 5 分钟一次）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void cleanupExpired() {
        RLock scanLock = redissonClient.getLock(TaskConstants.LOCK_DIRECT_UPLOAD_TIMEOUT_SCAN);
        if (!scanLock.tryLock()) {
            log.debug("已有实例在执行直传超时清理，跳过本轮");
            return;
        }
        try {
            doCleanupExpired();
        } catch (Exception e) {
            log.error("直传超时清理失败", e);
        } finally {
            if (scanLock.isHeldByCurrentThread()) {
                scanLock.unlock();
            }
        }
    }

    private void doCleanupExpired() {
        long now = System.currentTimeMillis();
        List<String> taskIds = directUploadRedis.scanTaskIds();
        for (String taskId : taskIds) {
            try {
                DirectUploadTask task = directUploadRedis.get(taskId);
                if (task == null || task.getExpiresAt() == null || task.getExpiresAt() > now) {
                    continue;
                }
                // 单任务锁：避免与正在完成的直传请求并发
                RLock taskLock = directUploadRedis.getLock(taskId);
                if (!taskLock.tryLock()) {
                    continue;
                }
                try {
                    DirectUploadTask latest = directUploadRedis.get(taskId);
                    if (latest == null || latest.getExpiresAt() == null || latest.getExpiresAt() > now) {
                        continue;
                    }
                    cleanup(latest);
                    log.warn("已清理过期直传任务: taskId={}, userId={}, fileSize={}",
                            taskId, latest.getUserId(), latest.getFileSize());
                } finally {
                    if (taskLock.isHeldByCurrentThread()) {
                        taskLock.unlock();
                    }
                }
            } catch (Exception e) {
                log.error("清理过期直传任务失败: taskId={}", taskId, e);
            }
        }
    }

    private void cleanup(DirectUploadTask task) {
        try {
            storageService.deleteFile(task.getObjectKey());
        } finally {
            userSpaceClient.releaseSpace(task.getUserId(), task.getFileSize());
            directUploadRedis.delete(task.getTaskId());
        }
    }
}
