package com.cloudrive.fileShareService.task;

import com.cloudrive.fileShareService.common.ShareConstants;
import com.cloudrive.fileShareService.service.FileShareService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分享过期消费者
 * <p>
 * 监听 Redis 延迟队列，秒级标记到期分享为失效；与
 * {@link ShareExpireTask} 定时任务形成「实时触发 + 批量兜底」双层保障。
 * 从原单体 {@code ShareExpireConsumer} 迁移，监听线程改为守护线程并支持优雅停止。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class ShareExpireConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ShareExpireConsumer.class);

    private final RedissonClient redissonClient;
    private final FileShareService fileShareService;

    /** 监听线程 */
    private Thread listenerThread;

    public ShareExpireConsumer(RedissonClient redissonClient, FileShareService fileShareService) {
        this.redissonClient = redissonClient;
        this.fileShareService = fileShareService;
    }

    /**
     * 应用启动后启动分享过期监听线程
     */
    @PostConstruct
    public void startShareExpireListener() {
        listenerThread = new Thread(() -> {
            RBlockingQueue<Long> shareExpireQueue =
                    redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
            logger.info("分享过期监听线程已启动，开始监听队列：{}", ShareConstants.Redis.SHARE_QUEUE);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Long expireShareId = shareExpireQueue.take();
                    if (expireShareId == null) {
                        continue;
                    }
                    boolean updated = fileShareService.markShareExpired(expireShareId);
                    if (updated) {
                        logger.info("分享ID: {} 已到期，成功标记为失效", expireShareId);
                    } else {
                        logger.warn("分享ID: {} 失效处理失败（可能已被手动取消/已过期）", expireShareId);
                    }
                }
            } catch (InterruptedException e) {
                logger.info("分享过期监听线程被中断，停止监听");
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                logger.error("处理单条分享过期时发生异常，线程继续监听", e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "share-expire-listener-thread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * 应用关闭时中断监听线程
     */
    @PreDestroy
    public void stopShareExpireListener() {
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}
