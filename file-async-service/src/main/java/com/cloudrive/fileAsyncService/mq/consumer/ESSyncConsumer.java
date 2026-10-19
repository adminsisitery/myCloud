package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.FileDocument;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import com.cloudrive.fileAsyncService.service.FileSearchService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
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
 * 消息体使用 pan-common 中的共享类 FileIndexMessage（生产端与消费端同 FQCN，
 * 否则 Jackson2JsonMessageConverter 的 __TypeId__ 无法反序列化）。
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
    public void handleInsert(FileIndexMessage message) {
        synchronize(message, false);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Es.DELETE_QUEUE)
    public void handleDelete(FileIndexMessage message) {
        synchronize(message, true);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Es.UPDATE_QUEUE)
    public void handleUpdate(FileIndexMessage message) {
        synchronize(message, false);
    }

    private void synchronize(FileIndexMessage message, boolean deletion) {
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
            FileDocument document = new FileDocument();
            document.setId(current.getId());
            document.setUserId(current.getUserId());
            document.setFileType(message.getFileType());
            document.setContent(message.getContent());
            String suffix = current.getFileSuffix() == null || current.getFileSuffix().isBlank()
                    ? ""
                    : "." + current.getFileSuffix();
            document.setFileName(current.getFilename()
                    + (Boolean.TRUE.equals(current.getIsFolder()) ? "" : suffix));
            fileSearchService.save(document);
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
