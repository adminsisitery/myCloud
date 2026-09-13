package com.ff.mycloud.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.enums.FolderDownloadStatus;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.FolderDownloadTask;
import com.ff.mycloud.model.vo.FolderDownloadTaskVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.redis.FolderDownloadRedis;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FolderDownloadTaskService;
import com.ff.mycloud.service.FolderService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FolderDownloadTaskServiceImpl implements FolderDownloadTaskService {
    private static final long ARCHIVE_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final int MAX_FILES = 20_000;
    private static final long MAX_SOURCE_BYTES = 200L * 1024 * 1024 * 1024;

    private final FolderDownloadRedis redis;
    private final FileInfoMapperService fileInfoService;
    private final FolderService folderService;
    private final StorageServiceFactory storageFactory;
    private final RabbitMqService rabbitMqService;

    public FolderDownloadTaskServiceImpl(FolderDownloadRedis redis,
                                         FileInfoMapperService fileInfoService,
                                         FolderService folderService,
                                         StorageServiceFactory storageFactory,
                                         RabbitMqService rabbitMqService) {
        this.redis = redis;
        this.fileInfoService = fileInfoService;
        this.folderService = folderService;
        this.storageFactory = storageFactory;
        this.rabbitMqService = rabbitMqService;
    }

    @Override
    public FolderDownloadTaskVO create(Long folderId) {
        Long userId = UserContext.getCurrentUserId();
        FileInfo folder = fileInfoService.findByIdAndUserId(folderId, userId);
        if (folder == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        if (!Boolean.TRUE.equals(folder.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER, "目标不是文件夹");
        }
        String rootPath = normalizeDirectory(folder.getLocalFilePath());
        List<FileInfo> subtree = fileInfoService.getSubTree(rootPath, userId);
        long fileCount = subtree.stream().filter(f -> !Boolean.TRUE.equals(f.getIsFolder())).count();
        long totalBytes = subtree.stream().filter(f -> !Boolean.TRUE.equals(f.getIsFolder()))
                .mapToLong(f -> f.getFileSize() == null ? 0 : f.getFileSize()).sum();
        if (fileCount > MAX_FILES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件数量超过20000，无法创建打包任务");
        if (totalBytes > MAX_SOURCE_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件夹总大小超过200GB");

        long now = System.currentTimeMillis();
        FolderDownloadTask task = new FolderDownloadTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setFolderId(folderId);
        task.setFolderName(folder.getFilename());
        task.setStatus(FolderDownloadStatus.PENDING);
        task.setTotalBytes(totalBytes);
        task.setMessage("等待后台打包");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        redis.save(task);
        try {
            rabbitMqService.sendFolderDownloadTask(task.getTaskId());
        } catch (RuntimeException e) {
            log.error("文件夹打包任务投递失败: taskId={}, userId={}, folderId={}", task.getTaskId(), userId, folderId, e);
            fail(task.getTaskId(), "MQ任务发送失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建文件夹下载任务失败");
        }
        log.info("文件夹打包任务创建完成: taskId={}, userId={}, folderId={}, totalBytes={}", task.getTaskId(), userId, folderId, totalBytes);
        return toVO(task);
    }

    @Override
    public FolderDownloadTaskVO getOwnedTask(String taskId) {
        return toVO(requireOwned(taskId));
    }

    @Override
    public FolderDownloadTask getDownloadableTask(String taskId) {
        FolderDownloadTask task = requireOwned(taskId);
        if (task.getStatus() != FolderDownloadStatus.SUCCESS || task.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "压缩包尚未生成");
        }
        if (task.getExpiresAt() <= System.currentTimeMillis()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "压缩包已过期");
        }
        return task;
    }

    @Override
    public void cancel(String taskId) {
        FolderDownloadTask task = requireOwned(taskId);
        redis.update(taskId, current -> {
            current.setCancelRequested(true);
            current.setUpdatedAt(System.currentTimeMillis());
            if (current.getStatus() == FolderDownloadStatus.PENDING) {
                current.setStatus(FolderDownloadStatus.CANCELLED);
                current.setMessage("任务已取消");
            }
        });
        if (task.getStatus() == FolderDownloadStatus.SUCCESS && task.getObjectKey() != null) {
            storageFactory.getStorageService().deleteFile(task.getObjectKey());
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.CANCELLED);
                current.setObjectKey(null);
                current.setMessage("任务已取消，临时压缩包已删除");
                current.setUpdatedAt(System.currentTimeMillis());
            });
        }
    }

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
                folderService.writeFolderArchive(task.getFolderId(), task.getUserId(), output, delta -> {
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
                storageFactory.getStorageService().uploadObject(finalObjectKey, input, Files.size(temp), "application/zip");
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
                try { Files.deleteIfExists(temp); } catch (Exception e) {
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

    private java.util.function.BooleanSupplier throttledCancellationCheck(String taskId) {
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

    private FolderDownloadTask requireOwned(String taskId) {
        FolderDownloadTask task = redis.get(taskId);
        if (task == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件夹下载任务不存在");
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) throw new BusinessException(ErrorCode.NO_PERMISSION);
        return task;
    }

    private FolderDownloadTaskVO toVO(FolderDownloadTask task) {
        return new FolderDownloadTaskVO(task.getTaskId(), task.getFolderName(), task.getStatus(),
                task.getTotalBytes(), task.getProcessedBytes(), task.getProgress(), task.getMessage(),
                task.getStatus() == FolderDownloadStatus.SUCCESS && task.getExpiresAt() > System.currentTimeMillis(),
                task.getExpiresAt());
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
        try { storageFactory.getStorageService().deleteFile(objectKey); } catch (Exception e) {
            log.warn("删除临时压缩包失败: objectKey={}", objectKey, e);
        }
    }

    private boolean terminal(FolderDownloadStatus status) {
        return status == FolderDownloadStatus.SUCCESS || status == FolderDownloadStatus.CANCELLED;
    }

    private String normalizeDirectory(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
