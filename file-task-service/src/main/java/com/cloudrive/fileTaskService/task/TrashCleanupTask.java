package com.cloudrive.fileTaskService.task;

import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.repositorys.FileInfoMapper;
import com.cloudrive.fileTaskService.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站 30 天自动清理
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.task.DeleteFileOverTime}：
 * 每天凌晨 3 点扫描回收站中 {@code isDeleted = 1 && deletedAt < now-30d} 的记录，
 * 无其他行引用同一物理对象时删除 OSS 对象，然后删除数据库记录。
 * </p>
 * <p>
 * 单体的空间额度在软删除时已经归还，因此这里只做物理对象与数据库记录的清理。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Slf4j
@Component
public class TrashCleanupTask {

    private final RedissonClient redissonClient;
    private final FileInfoMapper fileInfoMapper;
    private final StorageService storageService;

    public TrashCleanupTask(RedissonClient redissonClient,
                            FileInfoMapper fileInfoMapper,
                            StorageService storageService) {
        this.redissonClient = redissonClient;
        this.fileInfoMapper = fileInfoMapper;
        this.storageService = storageService;
    }

    /**
     * 清理过期回收站文件（每天 03:00）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupTrash() {
        RLock lock = redissonClient.getLock(TaskConstants.LOCK_TRASH_CLEANUP);
        if (!lock.tryLock()) {
            log.debug("已有实例在执行回收站清理，跳过本轮");
            return;
        }
        try {
            doCleanupTrash();
        } catch (Exception e) {
            log.error("回收站清理失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doCleanupTrash() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(TaskConstants.TRASH_RETENTION_DAYS);
        List<Long> fileIds = fileInfoMapper.findOverTimeFile(thirtyDaysAgo);
        int deleted = 0;
        for (Long fileId : fileIds) {
            try {
                Boolean isFolder = fileInfoMapper.findIsFolderById(fileId);
                String path = fileInfoMapper.findPathById(fileId);
                if (!Boolean.TRUE.equals(isFolder) && path != null && !path.isBlank()) {
                    // 同一物理对象可能被多个文件行引用（秒传/复制），有其它行时保留对象
                    String sha256Hash = fileSha256Hash(fileId);
                    if (sha256Hash == null || fileInfoMapper.countByHash(sha256Hash) <= 1) {
                        storageService.deleteFile(path);
                    }
                }
                fileInfoMapper.deleteById(fileId);
                deleted++;
            } catch (Exception e) {
                log.error("清理回收站文件失败: fileId={}", fileId, e);
            }
        }
        log.info("回收站清理完成: scanned={}, deleted={}", fileIds.size(), deleted);
    }

    /**
     * 查询文件哈希（回收站物理对象引用判断用）
     * <p>FileInfoMapper 的 @Select 已按需提供 path/isFolder，这里补充哈希查询。</p>
     */
    private String fileSha256Hash(Long fileId) {
        return fileInfoMapper.findSha256HashById(fileId);
    }
}
