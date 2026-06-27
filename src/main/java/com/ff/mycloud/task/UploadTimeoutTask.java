package com.ff.mycloud.task;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.UploadProgressRedis;
import com.ff.mycloud.service.UpLoadProgressService;
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

    /** 任务超时阈值：30 分钟 */
    private static final long TIMEOUT_MS = 30 * 60 * 1000;

    private final UploadProgressRedis uploadProgressRedis;
    private final UpLoadProgressService upLoadProgressService;

    /**
     * 构造方法注入依赖
     *
     * @param uploadProgressRedis  Redis上传进度操作类，用于扫描和获取上传任务
     * @param upLoadProgressService 上传进度服务，用于完成/失败上传任务的状态更新
     */
    public UploadTimeoutTask(UploadProgressRedis uploadProgressRedis,
                             UpLoadProgressService upLoadProgressService) {
        this.uploadProgressRedis = uploadProgressRedis;
        this.upLoadProgressService = upLoadProgressService;
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
}
