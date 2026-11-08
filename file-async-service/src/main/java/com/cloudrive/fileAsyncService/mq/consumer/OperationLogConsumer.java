package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.OperationLog;
import com.cloudrive.fileAsyncService.service.OperationLogMapperService;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者（移植单体 OperationLogConsumer）
 * <p>
 * 消费 log.queue，把 pan-common 共享消息 OperationLogMessage 映射为本地实体后落库 operation_log。
 * </p>
 * <p>
 * 幂等采用「先落库、后去重」：由 {@code operation_log.uk_msg_id} 唯一索引兜底，
 * 插入冲突即判定为重复消息并直接 ack。Redis 缓存只作快速短路，标记动作放在落库成功之后，
 * 保证「处理失败 → 重投」这条路径不会被误判成重复而丢消息。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogConsumer {

    private final OperationLogMapperService operationLogMapperService;
    private final MessageDedupService messageDedupService;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = MqConstants.Log.QUEUE)
    public void handleLog(OperationLogMessage message) {
        if (message == null) {
            return;
        }
        String msgId = message.getMsgId();
        // 快速路径：缓存命中说明此前已成功落库，直接 ack
        if (messageDedupService.isConsumed(msgId)) {
            log.debug("操作日志重复消息跳过（缓存命中）: msgId={}", msgId);
            return;
        }

        try {
            operationLogMapperService.save(toEntity(message));
        } catch (DuplicateKeyException e) {
            // 唯一索引拦截：并发消费、或缓存过期后 broker 重投才会走到这里
            log.debug("操作日志重复消息跳过（唯一索引）: msgId={}", msgId);
            messageDedupService.markConsumed(msgId);
            return;
        }

        messageDedupService.markConsumed(msgId);
        log.debug("操作日志已落库: msgId={}, operation={}", msgId, message.getOperation());
    }

    private OperationLog toEntity(OperationLogMessage message) {
        OperationLog entity = new OperationLog();
        entity.setUserId(message.getUserId());
        entity.setOperation(message.getOperation());
        entity.setFileId(message.getFileId());
        entity.setFilename(message.getFilename());
        entity.setFileSize(message.getFileSize());
        entity.setExtraInfo(message.getExtraInfo());
        entity.setClientIp(message.getClientIp());
        entity.setMsgId(message.getMsgId());
        entity.setCreateTime(message.getCreateTime());
        return entity;
    }
}
