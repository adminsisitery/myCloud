package com.cloudrive.fileTaskService.task;

import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.redis.FolderDownloadRedis;
import com.cloudrive.fileTaskService.service.StorageService;
import com.cloudrive.pan_common.domain.folder.FolderDownloadStatus;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件夹打包临时对象清理
 * <p>
 * 从 file-async-service 的 FolderDownloadCleanupTask 迁入。
 * 每 10 分钟扫描 folder:download:* 任务：
 * <ul>
 *   <li>SUCCESS 且超过 expiresAt：删除 OSS 临时 zip 并删任务；</li>
 *   <li>FAILED / CANCELLED 且超过 24 小时：删任务。</li>
 * </ul>
 * 文件夹打包的「执行」仍在 file-async-service，本服务只做过期清理。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Slf4j
@Component
public class FolderDownloadCleanupTask {

    private final RedissonClient redissonClient;
    private final FolderDownloadRedis folderDownloadRedis;
    private final StorageService storageService;

    public FolderDownloadCleanupTask(RedissonClient redissonClient,
                                     FolderDownloadRedis folderDownloadRedis,
                                     StorageService storageService) {
        this.redissonClient = redissonClient;
        this.folderDownloadRedis = folderDownloadRedis;
        this.storageService = storageService;
    }

    /**
     * 清理过期文件夹打包任务（每 10 分钟一次）
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void cleanupExpired() {
        RLock lock = redissonClient.getLock(TaskConstants.LOCK_FOLDER_DOWNLOAD_CLEANUP);
        if (!lock.tryLock()) {
            log.debug("已有实例在执行文件夹打包清理，跳过本轮");
            return;
        }
        try {
            doCleanupExpired();
        } catch (Exception e) {
            log.error("文件夹打包清理失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doCleanupExpired() {
        long now = System.currentTimeMillis();
        List<String> taskIds = folderDownloadRedis.scanTaskIds();
        for (String taskId : taskIds) {
            try {
                FolderDownloadTask task = folderDownloadRedis.get(taskId);
                if (task == null) {
                    continue;
                }
                if (task.getStatus() == FolderDownloadStatus.SUCCESS) {
                    if (task.getExpiresAt() > 0 && task.getExpiresAt() <= now) {
                        if (task.getObjectKey() != null) {
                            storageService.deleteFile(task.getObjectKey());
                        }
                        folderDownloadRedis.delete(taskId);
                        log.info("已清理过期文件夹打包临时包: taskId={}", taskId);
                    }
                } else if ((task.getStatus() == FolderDownloadStatus.FAILED
                        || task.getStatus() == FolderDownloadStatus.CANCELLED)
                        && task.getUpdatedAt() > 0
                        && task.getUpdatedAt() < now - TaskConstants.FOLDER_ARCHIVE_TTL_MS) {
                    folderDownloadRedis.delete(taskId);
                    log.info("已清理终态文件夹打包任务: taskId={}, status={}", taskId, task.getStatus());
                }
            } catch (Exception e) {
                log.error("清理文件夹打包任务失败: taskId={}", taskId, e);
            }
        }
    }
}
