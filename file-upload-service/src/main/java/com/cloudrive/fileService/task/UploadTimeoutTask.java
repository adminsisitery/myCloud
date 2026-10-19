package com.cloudrive.fileService.task;

import com.cloudrive.fileService.client.UserSpaceClient;
import com.cloudrive.fileService.model.entity.ChunkTask;
import com.cloudrive.fileService.model.entity.UploadTask;
import com.cloudrive.fileService.redis.ChunkUploadRedis;
import com.cloudrive.fileService.redis.UploadProgressRedis;
import com.cloudrive.fileService.service.StorageService;
import com.cloudrive.fileService.service.StorageServiceFactory;
import com.cloudrive.fileService.service.UpLoadProgressService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上传任务超时扫描
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.task.UploadTimeoutTask}。新模块是多实例部署，
 * 单体里用的 ShedLock 依赖没有带过来，这里直接用 Redisson 分布式锁保证同一时刻
 * 只有一个实例在扫描。
 * </p>
 * <p>
 * 普通上传：30 分钟没结清的预占额度视为失败任务，代为归还空间并标记失败。
 * 分片上传：24 小时没有活动的分片任务，清理 OSS 侧残片并归还空间。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@Component
public class UploadTimeoutTask {

    /** 普通上传超时时间：30 分钟 */
    private static final long NORMAL_TIMEOUT_MS = 30 * 60 * 1000L;

    /** 分片上传超时时间：24 小时 */
    private static final long CHUNK_TIMEOUT_MS = 24 * 60 * 60 * 1000L;

    private static final String LOCK_UPLOAD_TIMEOUT_SCAN = "lock:file_upload_timeout_scan";
    private static final String LOCK_CHUNK_TIMEOUT_SCAN = "lock:chunk_upload_timeout_scan";

    private final RedissonClient redissonClient;
    private final UploadProgressRedis uploadProgressRedis;
    private final ChunkUploadRedis chunkUploadRedis;
    private final UpLoadProgressService upLoadProgressService;
    private final UserSpaceClient userSpaceClient;
    private final StorageServiceFactory storageServiceFactory;

    public UploadTimeoutTask(RedissonClient redissonClient,
                             UploadProgressRedis uploadProgressRedis,
                             ChunkUploadRedis chunkUploadRedis,
                             UpLoadProgressService upLoadProgressService,
                             UserSpaceClient userSpaceClient,
                             StorageServiceFactory storageServiceFactory) {
        this.redissonClient = redissonClient;
        this.uploadProgressRedis = uploadProgressRedis;
        this.chunkUploadRedis = chunkUploadRedis;
        this.upLoadProgressService = upLoadProgressService;
        this.userSpaceClient = userSpaceClient;
        this.storageServiceFactory = storageServiceFactory;
    }

    /**
     * 扫描普通上传超时任务（每 5 分钟一次）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scanTimeoutTasks() {
        RLock lock = redissonClient.getLock(LOCK_UPLOAD_TIMEOUT_SCAN);
        if (!lock.tryLock()) {
            log.debug("已有实例在执行普通上传超时扫描，跳过本轮");
            return;
        }
        try {
            doScanTimeoutTasks();
        } catch (Exception e) {
            log.error("普通上传超时扫描失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doScanTimeoutTasks() {
        long cutoff = System.currentTimeMillis() - NORMAL_TIMEOUT_MS;
        List<String> taskIds = uploadProgressRedis.scanAllTaskIds();
        for (String taskId : taskIds) {
            try {
                UploadTask task = upLoadProgressService.getTask(taskId);
                if (task == null || !task.holdsUnsettledReservation() || task.getCreatedAt() > cutoff) {
                    continue;
                }
                Long userId = task.getUserId();
                if (userId == null) {
                    log.warn("上传任务缺少userId，无法自动归还空间: taskId={}", taskId);
                    continue;
                }
                long fileSize = task.getTotalSize();
                // 归还幂等闸：先原子占位，失败回滚，避免与异步线程重复归还
                if (!upLoadProgressService.tryMarkSpaceReleased(taskId)) {
                    continue;
                }
                try {
                    userSpaceClient.releaseSpace(userId, fileSize);
                } catch (RuntimeException e) {
                    upLoadProgressService.unmarkSpaceReleased(taskId);
                    log.error("超时任务归还空间失败，等待下一轮重试: taskId={}, userId={}", taskId, userId, e);
                    continue;
                }
                upLoadProgressService.completeUploadTask(taskId, false,
                        "上传超时，任务在" + task.getStatus() + "状态超过30分钟");
                log.warn("已清理超时上传任务并归还空间: taskId={}, userId={}, fileSize={}", taskId, userId, fileSize);
            } catch (Exception e) {
                log.error("扫描超时上传任务失败: taskId={}", taskId, e);
            }
        }
    }

    /**
     * 扫描分片上传超时任务（每 10 分钟一次）
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void scanChunkTimeoutTasks() {
        RLock lock = redissonClient.getLock(LOCK_CHUNK_TIMEOUT_SCAN);
        if (!lock.tryLock()) {
            log.debug("已有实例在执行分片上传超时扫描，跳过本轮");
            return;
        }
        try {
            doScanChunkTimeoutTasks();
        } catch (Exception e) {
            log.error("分片上传超时扫描失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doScanChunkTimeoutTasks() {
        long cutoff = System.currentTimeMillis() - CHUNK_TIMEOUT_MS;
        StorageService storageService = storageServiceFactory.getStorageService();
        for (String uploadId : chunkUploadRedis.scanAllTaskIds()) {
            try {
                ChunkTask task = chunkUploadRedis.getTask(uploadId);
                if (task == null) {
                    continue;
                }
                Long lastActivity = task.getLastActivityTime() != null
                        ? task.getLastActivityTime() : task.getCreatedAt();
                if (lastActivity == null || lastActivity > cutoff) {
                    continue;
                }
                Long userId = task.getUserId();
                Long fileSize = task.getFileSize();
                if (userId == null || fileSize == null) {
                    log.warn("分片任务缺少userId/fileSize，无法自动归还空间: uploadId={}", uploadId);
                    continue;
                }

                // 清理 OSS 侧数据：已合并的文件直接删对象，未合并的中止分片上传
                if (Boolean.TRUE.equals(task.getStorageCompleted())) {
                    storageService.deleteFile(task.getObjectKey());
                } else {
                    storageService.cancelUpload(task.getOssUploadId(), task.getObjectKey());
                }

                // 归还预占（spacePreAllocated 既做幂等闸，失败时回滚，下一轮继续补偿）
                if (Boolean.TRUE.equals(task.getSpacePreAllocated())) {
                    task.setSpacePreAllocated(false);
                    chunkUploadRedis.saveTask(task);
                    try {
                        userSpaceClient.releaseSpace(userId, fileSize);
                    } catch (RuntimeException e) {
                        task.setSpacePreAllocated(true);
                        chunkUploadRedis.saveTask(task);
                        log.error("分片超时任务归还空间失败，等待下一轮重试: uploadId={}, userId={}", uploadId, userId, e);
                        continue;
                    }
                }

                chunkUploadRedis.deleteTask(uploadId);
                log.warn("已清理超时分片任务并归还空间: uploadId={}, userId={}, fileSize={}", uploadId, userId, fileSize);
            } catch (Exception e) {
                log.error("扫描超时分片任务失败: uploadId={}", uploadId, e);
            }
        }
    }
}
