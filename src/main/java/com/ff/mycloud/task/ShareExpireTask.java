package com.ff.mycloud.task;


import com.ff.mycloud.service.impl.shareServiceImpl;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分享过期批量扫描定时任务
 *
 * 每天凌晨2点执行一次，批量扫描并标记所有到期的分享记录为失效状态。
 * 使用ShedLock分布式锁确保多节点部署时只有一个节点执行此任务。
 * 与{@link ShareExpireConsumer}配合，形成「批量兜底 + 实时触发」双层过期保障机制。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
public class ShareExpireTask {
    private static final Logger logger = LoggerFactory.getLogger(ShareExpireTask.class);

    private final shareServiceImpl shareServiceImpl;

    /**
     * 构造方法注入分享业务实现类
     *
     * @param shareServiceImpl 分享业务实现类，用于执行批量过期标记操作
     */
    public ShareExpireTask(shareServiceImpl shareServiceImpl) {
        this.shareServiceImpl = shareServiceImpl;
    }

    /**
     * 批量标记过期分享任务
     *
     * 每天凌晨2点自动执行（cron: 0 0 2 * * ?），扫描所有已到期的分享记录并将其标记为失效。
     * ShedLock分布式锁保证多节点环境下只有一个节点执行：
     * 最少锁定时长5分钟（防止时钟偏差导致的重复执行），
     * 最多锁定时长10分钟（防止死锁导致任务永久阻塞）。
     * 执行完成后记录节点ID日志，便于多节点排查。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(
        name = "markExpiredShares", // 锁的名称，必须唯一
        lockAtLeastFor = "PT5M",    // 最少锁定5分钟
        lockAtMostFor = "PT10M"     // 最多锁定10分钟
    )
    public void markExpiredShares() {
        String nodeId = System.getProperty("node.id", "unknown"); // 获取节点ID
        logger.info("节点 {} 尝试获取锁执行分享过期检查任务", nodeId);
        try {
            shareServiceImpl.markExpiredShares();
            logger.info("节点 {} 成功执行分享过期检查任务", nodeId);
        } catch (Exception e) {
            logger.error("节点 {} 执行分享过期检查任务失败", nodeId, e);
        }
    }

} 