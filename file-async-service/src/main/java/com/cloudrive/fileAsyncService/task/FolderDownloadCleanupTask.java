package com.cloudrive.fileAsyncService.task;

import com.cloudrive.fileAsyncService.service.FolderDownloadTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 文件夹压缩包过期清理（从单体 FolderDownloadCleanupTask 迁入）
 * <p>
 * 单体用 ShedLock 防多实例重复执行；本服务没有 ShedLock 依赖，改用 Redisson 锁。
 * 清理的是共享 Redis 中的任务记录与 OSS 上的临时压缩包。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FolderDownloadCleanupTask {

    private static final String LOCK_KEY = "lock:folder_download_cleanup";

    private final FolderDownloadTaskService folderDownloadTaskService;
    private final RedissonClient redissonClient;

    @Scheduled(cron = "0 */10 * * * ?")
    public void cleanupExpiredArchives() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 9, TimeUnit.MINUTES);
            if (!locked) {
                log.debug("文件夹打包清理任务未获得锁，跳过本轮");
                return;
            }
            folderDownloadTaskService.cleanupExpired();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("文件夹打包清理任务被中断", e);
        } catch (Exception e) {
            log.error("文件夹打包清理任务执行失败", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
