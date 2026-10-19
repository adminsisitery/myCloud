package com.cloudrive.fileShareService.task;

import com.cloudrive.fileShareService.service.FileShareService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分享过期批量扫描定时任务
 * <p>
 * 每天凌晨 2 点批量扫描并标记所有到期分享为失效状态，与
 * {@link ShareExpireConsumer} 的延迟队列实时处理形成「批量兜底 + 实时触发」双层保障。
 * </p>
 * <p>
 * 原单体使用 ShedLock；本服务未引入 shedlock 依赖，改用 Redisson 分布式锁，
 * 语义等价（lockAtMostFor 10 分钟，lockAtLeastFor 不单独模拟）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class ShareExpireTask {

    private static final Logger logger = LoggerFactory.getLogger(ShareExpireTask.class);

    /** 分布式锁名称 */
    private static final String LOCK_NAME = "lock:share_expire_mark";

    /** 最长锁定时间（分钟） */
    private static final long LOCK_LEASE_MINUTES = 10L;

    private final FileShareService fileShareService;
    private final RedissonClient redissonClient;

    public ShareExpireTask(FileShareService fileShareService, RedissonClient redissonClient) {
        this.fileShareService = fileShareService;
        this.redissonClient = redissonClient;
    }

    /**
     * 批量标记过期分享（每天 02:00 执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void markExpiredShares() {
        RLock lock = redissonClient.getLock(LOCK_NAME);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, LOCK_LEASE_MINUTES, TimeUnit.MINUTES);
            if (!locked) {
                logger.debug("分享过期扫描任务未获取到锁，跳过本轮");
                return;
            }
            fileShareService.markExpiredShares();
            logger.info("分享过期批量扫描完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("分享过期扫描任务获取锁被中断");
        } catch (RuntimeException e) {
            logger.error("分享过期扫描任务执行失败", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
