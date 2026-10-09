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
 *   超时失败 → 线程已无法执行 → 由本任务扫描时代为归还空间（依赖任务快照里的 userId）
 *
 * 扫描的判定依据（重要）：<b>不是 status，而是任务快照里的 spaceReserved 标记</b>。
 *   取消 → 状态变终态 CANCELLED，而释放只由持有上传权的异步线程负责；
 *   若线程从未执行（JVM 重启 / 线程池拒绝 / 排队时进程退出），状态是终态、
 *   归还却从未发生。此时按 status 过滤会直接跳过它，空间永久泄漏。
 *   改用「是否持有未结清的预占额度」判定后，取消后线程未执行、以及
 *   「预占过但早已是终态」的历史遗漏都能被兜住。
 *   上传成功落库的任务会在异步线程里 clearSpaceReserved 结清，因此不会被误退（那是资损方向）。
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
     * 对创建时间超过30分钟、且仍持有「未结清预占额度」的任务：先代为归还空间，再标记失败
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

            // 判定依据是「还有没有未结清的预占额度」，不是 status。
            // 只看 status 会漏掉一种真实情况：用户取消 → 状态变终态 CANCELLED，
            // 而释放只由持有上传权的异步线程负责 → 线程从未执行（JVM 重启 / 线程池拒绝）
            // 时没有任何人归还，终态又会被下面原本文的状态过滤直接跳过 → 空间永久泄漏。
            if (!task.holdsUnsettledReservation()) {
                continue;
            }

            UploadTaskStatus status = task.getStatus();

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

            // 1. 先归还预占空间（幂等闸保护）
            //    顺序不能反：completeUploadTask 会把状态置为终态 FAILED，而本方法开头就跳过终态任务，
            //    一旦先标失败，归还就永远失去第二次机会（即便本次归还抛异常也没人再来重试）。
            try {
                releaseSpaceOnce(taskId, task);
            } catch (Exception e) {
                log.error("超时任务归还空间失败，保留任务待下轮重试 - taskId={}, error={}",
                        taskId, e.getMessage(), e);
                continue;
            }

            // 2. 标记失败（TTL 继承原有，不会被刷新）
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
                if (Boolean.TRUE.equals(task.getStorageCompleted())) {
                    storageServiceFactory.getStorageService().deleteFile(task.getObjectKey());
                } else {
                    storageServiceFactory.getStorageService()
                            .cancelUpload(task.getOssUploadId(), task.getObjectKey());
                }

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

    /**
     * 归还预占空间（幂等）
     * <p>先用 {@code tryMarkSpaceReleased} 抢占「本次归还」的执行权：只有首次返回 true
     * 才真正归还，避免正常失败路径与超时扫描对同一任务重复归还。</p>
     * <p>归还失败时先回滚标记再原样抛出，让任务保持非终态、下一轮扫描还能重试 ——
     * 若只置位不回滚，这把「幂等闸」会把之后所有重试都挡回去，空间永久漏还。</p>
     *
     * @param taskId 任务ID
     * @param task   任务快照（提供 userId 与 totalSize）
     */
    private void releaseSpaceOnce(String taskId, UploadTask task) {
        Long userId = task.getUserId();
        if (userId == null) {
            // 加 userId 字段之前写入 Redis 的历史任务，无法判断这笔空间该还给谁
            log.warn("超时任务缺少 userId，无法自动归还预占空间 - taskId={}, size={}，需人工核对",
                    taskId, task.getTotalSize());
            return;
        }
        if (!upLoadProgressService.tryMarkSpaceReleased(taskId)) {
            return;
        }
        try {
            userCenterMapperService.releaseSpace(userId, task.getTotalSize());
            log.info("超时任务已归还预占空间 - taskId={}, userId={}, size={}",
                    taskId, userId, task.getTotalSize());
        } catch (RuntimeException e) {
            upLoadProgressService.unmarkSpaceReleased(taskId);
            throw e;
        }
    }
}
