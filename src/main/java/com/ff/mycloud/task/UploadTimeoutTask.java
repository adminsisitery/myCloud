package com.ff.mycloud.task;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.ChunkTask;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.redis.UploadProgressRedis;
import com.ff.mycloud.service.StorageServiceFactory;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上传任务超时扫描定时任务
 *
 * 每5分钟扫描一次 Redis 中仍在进行的上传任务，
 * 将超过30分钟未完成的任务标记为失败。
 * 使用ShedLock分布式锁确保多节点部署时只有一个节点执行此任务。
 *
 * 与正常失败的区别：
 *   正常失败 → 线程池 catch 异常 → 立即释放空间 + 删临时文件
 *   超时失败 → 线程已无法执行 → 只能标记状态，空间无法自动释放
 *
 * TODO: UploadTask 加 userId 后可自动释放空间
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
public class UploadTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(UploadTimeoutTask.class);

    /** 普通上传任务超时阈值：30 分钟 */
    private static final long TIMEOUT_MS = 30 * 60 * 1000;

    /** 分片上传任务超时阈值：24 小时（超时未完成则 abort OSS + 归还空间） */
    private static final long CHUNK_TIMEOUT_MS = 24 * 60 * 60 * 1000;

    private final UploadProgressRedis uploadProgressRedis;
    private final UpLoadProgressService upLoadProgressService;
    private final ChunkUploadRedis chunkUploadRedis;
    private final StorageServiceFactory storageServiceFactory;
    private final UserCenterMapperService userCenterMapperService;

    public UploadTimeoutTask(UploadProgressRedis uploadProgressRedis,
                             UpLoadProgressService upLoadProgressService,
                             ChunkUploadRedis chunkUploadRedis,
                             StorageServiceFactory storageServiceFactory,
                             UserCenterMapperService userCenterMapperService) {
        this.uploadProgressRedis = uploadProgressRedis;
        this.upLoadProgressService = upLoadProgressService;
        this.chunkUploadRedis = chunkUploadRedis;
        this.storageServiceFactory = storageServiceFactory;
        this.userCenterMapperService = userCenterMapperService;
    }

    /**
     * 扫描并处理超时上传任务
     *
     *
     * 每5分钟执行一次 遍历Redis中所有上传任务
     * 将创建时间超过30分钟且仍处于PENDING或UPLOADING状态的任务标记为失败
     * ShedLock分布式锁保证多节点环境下只有一个节点执行
     * 最少锁定时长1分钟，最多锁定时长5分钟
     * 处理完成后记录超时任务数量日志
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @SchedulerLock(name = "uploadTimeoutScan",
                   lockAtLeastFor = "PT1M",
                   lockAtMostFor = "PT5M")
    public void scanTimeoutTasks() {
        long cutoff = System.currentTimeMillis() - TIMEOUT_MS;

        List<String> taskIds = uploadProgressRedis.scanAllTaskIds();
        if (taskIds.isEmpty()) {
            return;
        }

        int timeoutCount = 0;
        for (String taskId : taskIds) {
            UploadTask task = uploadProgressRedis.getTask(taskId);
            if (task == null) {
                continue;
            }

            // 只处理非终结状态
            UploadTaskStatus status = task.getStatus();
            if (status != UploadTaskStatus.PENDING && status != UploadTaskStatus.UPLOADING) {
                continue;
            }

            // 未超时
            if (task.getCreatedAt() > cutoff) {
                continue;
            }

            log.warn("上传任务超时 - taskId={}, fileName={}, size={}, status={}, ageMinutes={}",
                    task.getId(),
                    task.getFilename(),
                    task.getTotalSize(),
                    status,
                    (System.currentTimeMillis() - task.getCreatedAt()) / 60000);

            // 标记失败（TTL 继承原有，不会被刷新）
            upLoadProgressService.completeUploadTask(taskId, false,
                    "上传超时，任务在" + status + "状态超过30分钟");

            timeoutCount++;
        }

        if (timeoutCount > 0) {
            log.info("上传超时扫描完成 - 清理超时任务 {} 个", timeoutCount);
        }
    }

    /**
     * 扫描并清理超时分片上传任务
     *
     * 每10分钟执行一次，遍历 Redis 中所有 chunk:upload:* 任务，
     * 将创建时间超过24小时仍未完成的任务清理：
     *   1. 调用 OSS abortMultipartUpload 释放分片存储
     *   2. 归还预占的用户空间
     *   3. 删除 Redis 中所有相关 key
     */
    @Scheduled(cron = "0 */10 * * * ?")
    @SchedulerLock(name = "chunkUploadTimeoutScan",
                   lockAtLeastFor = "PT1M",
                   lockAtMostFor = "PT10M")
    public void scanChunkTimeoutTasks() {
        long cutoff = System.currentTimeMillis() - CHUNK_TIMEOUT_MS;

        List<String> uploadIds = chunkUploadRedis.scanAllTaskIds();
        if (uploadIds.isEmpty()) {
            return;
        }

        int timeoutCount = 0;
        for (String uploadId : uploadIds) {
            ChunkTask task = chunkUploadRedis.getTask(uploadId);
            if (task == null) {
                continue;
            }

            // 用 lastActivityTime 判断超时（兜底 createdAt）
            long lastActive = task.getLastActivityTime() != null
                    ? task.getLastActivityTime() : task.getCreatedAt();
            if (lastActive > cutoff) {
                continue;
            }

            log.warn("分片上传任务超时 - uploadId={}, fileName={}, size={}, idleHours={}",
                    task.getUploadId(),
                    task.getFileName(),
                    task.getFileSize(),
                    (System.currentTimeMillis() - lastActive) / 3600000);

            try {
                // 1. 取消 OSS 分片上传
                storageServiceFactory.getStorageService()
                        .cancelUpload(task.getOssUploadId(), task.getObjectKey());

                // 2. 归还预占空间
                userCenterMapperService.releaseSpace(task.getUserId(), task.getFileSize());

                // 3. 清理 Redis
                chunkUploadRedis.deleteTask(uploadId);

                timeoutCount++;
            } catch (Exception e) {
                log.error("清理超时分片任务失败 - uploadId={}, error={}",
                        uploadId, e.getMessage(), e);
            }
        }

        if (timeoutCount > 0) {
            log.info("分片上传超时扫描完成 - 清理超时任务 {} 个", timeoutCount);
        }
    }
}
