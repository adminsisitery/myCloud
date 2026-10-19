package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.FileDocument;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import com.cloudrive.fileAsyncService.service.FileSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ES 索引同步消费者（移植单体 ESSyncConsumer）
 * <p>
 * 消息只带文件 ID 与候选内容，处理时以 MySQL 实时数据为准：
 * 文件已删除则删索引；归属/文件夹状态变化以 DB 为准；同一文件加分布式锁串行处理。
 * 重试耗尽后由 Broker 的 DLX policy 转入死信队列。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESSyncConsumer {

    private final FileSearchService fileSearchService;
    private final FileInfoMapperService files;
    private final RedissonClient redisClient;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Es.INSERT_QUEUE)
    public void handleInsert(FileDocument message) {
        synchronize(message, false);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Es.DELETE_QUEUE)
    public void handleDelete(FileDocument message) {
        synchronize(message, true);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Es.UPDATE_QUEUE)
    public void handleUpdate(FileDocument message) {
        synchronize(message, false);
    }

    private void synchronize(FileDocument message, boolean deletion) {
        if (message == null || message.getId() == null) {
            throw new IllegalArgumentException("索引消息缺少文件ID");
        }
        Long fileId = message.getId();
        RLock lock = redisClient.getLock(MqConstants.INDEX_LOCK_PREFIX + fileId);
        boolean locked = false;
        try {
            locked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("索引繁忙，请重试");
            }
            FileInfo current = files.getById(fileId);
            if (current == null || Boolean.TRUE.equals(current.getIsDeleted())) {
                fileSearchService.del(fileId);
                return;
            }
            if (deletion) {
                return;
            }
            if (current.getUserId() != null && message.getUserId() != null
                    && !current.getUserId().equals(message.getUserId())) {
                log.warn("索引消息归属不一致，跳过: fileId={}, messageUserId={}, dbUserId={}",
                        fileId, message.getUserId(), current.getUserId());
                return;
            }
            message.setUserId(current.getUserId());
            String suffix = current.getFileSuffix() == null || current.getFileSuffix().isBlank()
                    ? ""
                    : "." + current.getFileSuffix();
            message.setFileName(current.getFilename()
                    + (Boolean.TRUE.equals(current.getIsFolder()) ? "" : suffix));
            fileSearchService.save(message);
            log.debug("ES 索引已同步: fileId={}, deletion={}", fileId, deletion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("索引任务已中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
