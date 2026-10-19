package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.common.util.EmailUtil;
import com.cloudrive.fileAsyncService.model.entity.UsageMessage;
import com.cloudrive.fileAsyncService.model.entity.User;
import com.cloudrive.fileAsyncService.repositorys.UserMapper;
import com.cloudrive.fileAsyncService.ws.WsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用量通知消费者（从单体 UasgeCheckConsumer 迁入）
 * <p>收到用量消息后通过 WebSocket 实时推送 + 邮件通知用户。
 * 对不可恢复的错误（无 userId、用户不存在、无邮箱）直接拒绝且不重新入队，消息进入死信队列；
 * 对可恢复的错误（Redis/DB 异常）抛异常触发重试，重试耗尽后进入死信队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageNotificationConsumer {

    private final UserMapper userMapper;
    private final WsService wsService;
    private final RedissonClient redissonClient;

    /**
     * 处理用量消息
     * <p>重试策略：可恢复异常（DB/Redis）最多重试 3 次，间隔 5 秒；不可恢复错误直接投死信。</p>
     */
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000),
            retryFor = {Exception.class},
            noRetryFor = {AmqpRejectAndDontRequeueException.class}
    )
    @RabbitListener(queues = MqConstants.Usage.QUEUE)
    public void check(UsageMessage usageMessage) {
        Long userId = usageMessage.getUserId();
        if (userId == null) {
            log.error("收到无userId的用量消息，消息体: {}", usageMessage);
            throw new AmqpRejectAndDontRequeueException("UsageMessage.userId 为空，消息格式错误");
        }

        User user;
        try {
            user = userMapper.selectById(userId);
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
        String dedupKey = MqConstants.Usage.NOTIFIED_PREFIX + userId;
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
        }
    }

    /**
     * 重试全部失败后的兜底处理，异常继续传播到 Listener Container，由 DLX 路由进死信队列
     */
    @Recover
    public void recover(Exception e, UsageMessage usageMessage) {
        log.error("用量通知处理失败，重试3次已耗尽，消息进入死信队列。"
                        + " userId={}, usageSize={}, totalSize={}, message={}",
                usageMessage.getUserId(), usageMessage.getUsageSize(),
                usageMessage.getTotalSize(), usageMessage.getMessage(), e);
    }
}
