package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.util.EmailUtil;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.entity.UsageMessage;
import com.ff.mycloud.service.impl.UserMapperServiceImpl;
import com.ff.mycloud.ws.service.WsService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用量检查消费者
 * <p>监听用量消息队列，收到消息后通过 WebSocket 实时推送 + 邮件通知用户。
 * 对不可恢复的错误（无 userId、用户不存在等）直接拒绝且不重新入队，消息进入死信队列。
 * 对可恢复的错误（Redis异常、DB异常）抛异常触发重试，最终进入死信队列。</p>
 *
 * @author 飞飞
 * @date 2026/8/5
 */
@Component
public class UasgeCheckConsumer {
    private static final Logger log = LoggerFactory.getLogger(UasgeCheckConsumer.class);

    private static final String USAGE_NOTIFIED_PREFIX = "usage:notified:";

    private final UserMapperServiceImpl userMapperServiceImpl;
    private final WsService wsService;
    private final RedissonClient redissonClient;

    public UasgeCheckConsumer(UserMapperServiceImpl userMapperServiceImpl,
                               WsService wsService,
                               RedissonClient redissonClient) {
        this.userMapperServiceImpl = userMapperServiceImpl;
        this.wsService = wsService;
        this.redissonClient = redissonClient;
    }

    /**
     * 处理用量消息
     * <p>队列和死信由 {@link com.ff.mycloud.mq.config.RabbitMQConfig} 声明，
     * 此处仅监听已存在的队列。</p>
     *
     * <p>重试策略：可恢复异常（DB/Redis）最多重试3次，间隔5秒。
     * 不可恢复错误（userId为空、用户不存在、无邮箱）直接投死信。
     * 全部重试耗尽后消息进入死信队列，由运维人员处理。</p>
     */
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000),
            retryFor = {Exception.class},
            noRetryFor = {AmqpRejectAndDontRequeueException.class}
    )
    @RabbitListener(queues = CommonConstants.USAGE.USAGE_QUEUE)
    public void check(UsageMessage usageMessage) {
        Long userId = usageMessage.getUserId();
        if (userId == null) {
            log.error("收到无userId的用量消息，消息体: {}", usageMessage);
            throw new AmqpRejectAndDontRequeueException("UsageMessage.userId 为空，消息格式错误");
        }

        User user;
        try {
            user = userMapperServiceImpl.findOneByUserId(userId);
        } catch (Exception e) {
            log.error("查询用户失败 - userId={}, 将重试", userId, e);
            throw new IllegalStateException("DB查询异常，触发重试", e);
        }

        if (user == null) {
            log.error("用户不存在 - userId={}", userId);
            throw new AmqpRejectAndDontRequeueException("用户" + userId + "不存在");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.error("用户无邮箱 - userId={}", userId);
            throw new AmqpRejectAndDontRequeueException("用户" + userId + "无邮箱");
        }

        // 同一天同一用户只发一次
        String dedupKey = USAGE_NOTIFIED_PREFIX + userId;
        RBucket<String> bucket;
        try {
            bucket = redissonClient.getBucket(dedupKey);
            if (bucket.isExists()) {
                log.debug("用户{}今日已通知过，跳过", userId);
                return;
            }
        } catch (Exception e) {
            log.error("Redis去重检查失败 - userId={}, 将重试", userId, e);
            throw new IllegalStateException("Redis异常，触发重试", e);
        }

        // WebSocket 实时推送（推送失败不影响邮件发送）
        try {
            wsService.sendMsg(userId, usageMessage.getMessage());
        } catch (Exception e) {
            log.warn("WebSocket 推送失败 - userId={}, error={}", userId, e.getMessage());
        }

        // 邮件通知
        boolean sent = EmailUtil.sendUsage(user.getEmail(), usageMessage.getMessage());
        if (sent) {
            try {
                bucket.set("1", Duration.ofDays(1));
            } catch (Exception e) {
                log.warn("Redis去重标记写入失败，不影响已发送的通知 - userId={}", userId, e);
            }
            log.info("用量通知已发送 - userId={}, email={}", userId, user.getEmail());
        } else {
            log.warn("用量邮件发送失败 - userId={}, email={}", userId, user.getEmail());
            // 邮件发送失败不抛异常——不阻塞重试，标记写入失败也不进死信
        }
    }

    /**
     * 重试全部失败后的兜底处理
     * <p>记录完整消息内容，方便运维从死信队列中排查。
     * 此方法执行完毕后，异常仍会传播到 Listener Container，
     * 配合 DLX 配置将消息路由到死信队列。</p>
     */
    @Recover
    public void recover(Exception e, UsageMessage usageMessage) {
        log.error("用量通知处理失败，重试3次已耗尽，消息进入死信队列。"
                + " userId={}, usageSize={}, totalSize={}, message={}",
                usageMessage.getUserId(), usageMessage.getUsageSize(),
                usageMessage.getTotalSize(), usageMessage.getMessage(), e);
    }
}
