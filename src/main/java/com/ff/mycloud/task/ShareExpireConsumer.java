package com.ff.mycloud.task;

import com.ff.mycloud.common.constant.ShareConstants;
import com.ff.mycloud.service.impl.shareServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct; // 注意是 jakarta 而非 javax（Spring Boot 3+）
import java.util.concurrent.TimeUnit;

/**
 * 分享过期消费者：监听Redis延迟队列，秒级标记到期分享为失效
 * 配合定时任务形成「实时触发+批量兜底」双层过期保障
 * @author 飞飞
 * @date 2026/3/14
 */
@Slf4j
@Component
public class ShareExpireConsumer {

    // 注入项目已配置的Redisson客户端（和你shareQueueRedis中用的是同一个）
    private final RedissonClient redissonClient;
    // 注入你的分享业务实现类（已包含新增的markShareExpired方法）
    private final shareServiceImpl shareServiceImpl;

    /**
     * 构造方法注入依赖
     *
     * @param redissonClient   Redisson客户端，用于获取阻塞队列
     * @param shareServiceImpl 分享业务实现类，用于执行分享过期标记操作
     */
    public ShareExpireConsumer(RedissonClient redissonClient, shareServiceImpl shareServiceImpl) {
        this.redissonClient = redissonClient;
        this.shareServiceImpl = shareServiceImpl;
    }

    /**
     * 项目启动时自动启动分享过期监听线程
     *
     * 创建独立守护线程，阻塞监听Redis延迟队列中的到期分享ID，
     * 收到消息后调用业务层标记分享为失效状态。
     * 配合{@link ShareExpireTask}定时任务形成「实时触发 + 批量兜底」双层过期保障机制。
     *
     * @PostConstruct 确保Spring容器初始化完该Bean后立即执行
     */
    @PostConstruct
    public void startShareExpireListener() {
        // 新建独立线程监听队列，避免阻塞主线程
        new Thread(() -> {
            // 获取和你延迟队列中同名的阻塞队列（确保队列名称一致）
            RBlockingQueue<Long> shareExpireQueue = redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
            log.info("分享过期监听线程已启动，开始监听队列：{}", ShareConstants.Redis.SHARE_QUEUE);

            try {
                // 无限循环监听，直到线程被中断
                while (!Thread.currentThread().isInterrupted()) {
                    // 阻塞等待：队列空时休眠，有到期ID时自动唤醒（几乎不耗CPU）
                    Long expireShareId = shareExpireQueue.take();

                    if (expireShareId != null) {
                        // 调用单条更新方法，标记该分享为失效
                        boolean updateSuccess = shareServiceImpl.markShareExpired(expireShareId);
                        if (updateSuccess) {
                            log.info("分享ID: {} 已到期，成功标记为失效", expireShareId);
                        } else {
                            log.warn("分享ID: {} 失效处理失败（可能已被手动取消/已过期）", expireShareId);
                        }
                    }
                }
            } catch (InterruptedException e) {
                // 线程被中断时记录日志，并重置中断状态
                log.error("分享过期监听线程被中断，停止监听", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 其他异常不终止线程，记录日志后休眠1秒避免死循环
                log.error("处理单条分享过期时发生异常，线程将继续监听", e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "share-expire-listener-thread").start(); // 线程命名，方便排查问题
    }
}