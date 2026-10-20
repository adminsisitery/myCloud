package com.cloudrive.fileTaskService.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.model.entity.ShareRecord;
import com.cloudrive.fileTaskService.repositorys.ShareRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 分享过期标记
 * <p>
 * 从 file-share-service 的 ShareExpireTask 迁入。每天凌晨 2 点把
 * {@code expire_time < now && is_expired = 0} 的分享记录标记为已过期。
 * 与 share-service 共用同一张表与同一把分布式锁 key，滚动发布期间新旧实例互斥。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Slf4j
@Component
public class ShareExpireTask {

    private final RedissonClient redissonClient;
    private final ShareRecordMapper shareRecordMapper;

    public ShareExpireTask(RedissonClient redissonClient, ShareRecordMapper shareRecordMapper) {
        this.redissonClient = redissonClient;
        this.shareRecordMapper = shareRecordMapper;
    }

    /**
     * 标记过期分享（每天 02:00）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void markExpiredShares() {
        RLock lock = redissonClient.getLock(TaskConstants.LOCK_SHARE_EXPIRE_MARK);
        if (!lock.tryLock()) {
            log.debug("已有实例在执行分享过期标记，跳过本轮");
            return;
        }
        try {
            doMarkExpiredShares();
        } catch (Exception e) {
            log.error("分享过期标记失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doMarkExpiredShares() {
        LocalDateTime now = LocalDateTime.now();
        int updated = shareRecordMapper.update(null, new LambdaUpdateWrapper<ShareRecord>()
                .set(ShareRecord::getIsExpired, true)
                .set(ShareRecord::getUpdatedAt, now)
                .lt(ShareRecord::getExpireTime, now)
                .eq(ShareRecord::getIsExpired, false));
        log.info("分享过期标记完成: updated={}", updated);
    }
}
