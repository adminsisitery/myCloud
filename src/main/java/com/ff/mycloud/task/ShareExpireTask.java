package com.ff.mycloud.task;


import com.ff.mycloud.service.impl.shareServiceImpl;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShareExpireTask {
    private static final Logger logger = LoggerFactory.getLogger(ShareExpireTask.class);

    private final shareServiceImpl shareServiceImpl;

    public ShareExpireTask(shareServiceImpl shareServiceImpl) {
        this.shareServiceImpl = shareServiceImpl;
    }

    /**
     * 每天凌晨2点执行一次
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