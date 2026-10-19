package com.cloudrive.fileAsyncService.service.impl;

import com.cloudrive.pan_common.domain.folder.FolderDownloadStatus;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import com.cloudrive.fileAsyncService.redis.FolderDownloadRedis;
import com.cloudrive.fileAsyncService.service.FolderArchiveService;
import com.cloudrive.fileAsyncService.service.FolderDownloadTaskService;
import com.cloudrive.fileAsyncService.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 文件夹打包任务实现（从单体 FolderDownloadTaskServiceImpl 迁入，仅保留 process/cleanupExpired）
 * <p>任务状态存于共享 Redis（与单体 folder:download:{taskId} 同一批 key），
 * 临时 ZIP 上传到 OSS temp/folder-download/user_{userId}/{taskId}.zip。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderDownloadTaskServiceImpl implements FolderDownloadTaskService {

    private static final long ARCHIVE_TTL_MS = TimeUnit.HOURS.toMillis(24);

    private final FolderDownloadRedis redis;
    private final FolderArchiveService folderArchiveService;
    private final StorageService storageService;

    @Override
    public void process(String taskId) {
        RLock lock = redis.processingLock(taskId);
        boolean locked = false;
        Path temp = null;
        String objectKey = null;
        try {
            // 不设置固定租期，使用 Redisson watchdog 自动续租，避免超大目录打包超过租期后重复消费。
            locked = lock.tryLock();
            if (!locked) return;
            FolderDownloadTask task = redis.get(taskId);
            if (task == null || terminal(task.getStatus()) || task.isCancelRequested()) return;
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.PACKAGING);
                current.setMessage("正在打包文件夹");
                current.setUpdatedAt(System.currentTimeMillis());
            });

            log.info("文件夹打包开始: taskId={}, userId={}, folderId={}", taskId, task.getUserId(), task.getFolderId());
            temp = Files.createTempFile("mycloud-folder-" + taskId + "-", ".zip");
            long[] processed = {0};
            long[] lastSaved = {0};
            try (OutputStream output = Files.newOutputStream(temp)) {
                folderArchiveService.writeFolderArchive(task.getFolderId(), task.getUserId(), output, delta -> {
                    processed[0] += delta;
                    if (processed[0] - lastSaved[0] >= 1024 * 1024) {
                        updatePackagingProgress(taskId, processed[0]);
                        lastSaved[0] = processed[0];
                    }
                }, throttledCancellationCheck(taskId));
            }
            updatePackagingProgress(taskId, processed[0]);
            if (isCancelled(taskId)) throw new CancellationException();

            objectKey = "temp/folder-download/user_" + task.getUserId() + "/" + taskId + ".zip";
            String finalObjectKey = objectKey;
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.UPLOADING);
                current.setProgress(0.95);
                current.setMessage("正在上传临时压缩包");
                current.setUpdatedAt(System.currentTimeMillis());
            });
            try (InputStream input = Files.newInputStream(temp)) {
                storageService.uploadObject(finalObjectKey, input, Files.size(temp), "application/zip");
            }
            if (isCancelled(taskId)) throw new CancellationException();
            long expiresAt = System.currentTimeMillis() + ARCHIVE_TTL_MS;
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.SUCCESS);
                current.setProcessedBytes(current.getTotalBytes());
                current.setProgress(1.0);
                current.setObjectKey(finalObjectKey);
                current.setMessage("打包完成");
                current.setExpiresAt(expiresAt);
                current.setUpdatedAt(System.currentTimeMillis());
            });
            log.info("文件夹打包完成: taskId={}, userId={}, processedBytes={}", taskId, task.getUserId(), processed[0]);
        } catch (CancellationException e) {
            log.info("文件夹打包取消: taskId={}", taskId);
            if (objectKey != null) safeDeleteObject(objectKey);
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.CANCELLED);
                current.setObjectKey(null);
                current.setMessage("任务已取消");
                current.setUpdatedAt(System.currentTimeMillis());
            });
        } catch (Exception e) {
            log.error("文件夹打包失败: taskId={}", taskId, e);
            if (objectKey != null) safeDeleteObject(objectKey);
            fail(taskId, "打包失败: " + e.getMessage());
            throw new RuntimeException("文件夹异步打包失败: " + taskId, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception e) {
                    log.warn("删除打包临时文件失败: taskId={}, path={}", taskId, temp, e);
                }
            }
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (String taskId : redis.scanTaskIds()) {
            FolderDownloadTask task = redis.get(taskId);
            if (task == null) continue;
            if (task.getStatus() == FolderDownloadStatus.SUCCESS && task.getExpiresAt() > 0 && task.getExpiresAt() <= now) {
                safeDeleteObject(task.getObjectKey());
                redis.delete(taskId);
            } else if ((task.getStatus() == FolderDownloadStatus.FAILED || task.getStatus() == FolderDownloadStatus.CANCELLED)
                    && task.getUpdatedAt() < now - ARCHIVE_TTL_MS) {
                redis.delete(taskId);
            }
        }
    }

    private BooleanSupplier throttledCancellationCheck(String taskId) {
        long[] nextCheck = {0};
        boolean[] cancelled = {false};
        return () -> {
            long now = System.currentTimeMillis();
            if (now >= nextCheck[0]) {
                cancelled[0] = isCancelled(taskId);
                nextCheck[0] = now + 300;
            }
            return cancelled[0];
        };
    }

    private void updatePackagingProgress(String taskId, long processed) {
        redis.update(taskId, current -> {
            current.setProcessedBytes(Math.min(processed, current.getTotalBytes()));
            current.setProgress(current.getTotalBytes() == 0 ? 0.9
                    : Math.min(0.9, processed / (double) current.getTotalBytes() * 0.9));
            current.setUpdatedAt(System.currentTimeMillis());
        });
    }

    private boolean isCancelled(String taskId) {
        FolderDownloadTask task = redis.get(taskId);
        return task == null || task.isCancelRequested() || task.getStatus() == FolderDownloadStatus.CANCELLED;
    }

    private void fail(String taskId, String message) {
        redis.update(taskId, current -> {
            current.setStatus(FolderDownloadStatus.FAILED);
            current.setMessage(message == null ? "打包失败" : message);
            current.setUpdatedAt(System.currentTimeMillis());
        });
    }

    private void safeDeleteObject(String objectKey) {
        if (objectKey == null) return;
        try {
            storageService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("删除临时压缩包失败: objectKey={}", objectKey, e);
        }
    }

    private boolean terminal(FolderDownloadStatus status) {
        return status == FolderDownloadStatus.SUCCESS || status == FolderDownloadStatus.CANCELLED;
    }
}
