package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.OperationLog;
import com.cloudrive.fileAsyncService.service.OperationLogMapperService;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者（移植单体 OperationLogConsumer）
 * <p>
 * 消费 log.queue，把 pan-common 共享消息 OperationLogMessage 映射为本地实体后落库 operation_log；
 * 用 msgId 做 Redis 幂等，避免 broker 重投重复写。
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
        if (!messageDedupService.tryConsume(message.getMsgId())) {
            log.debug("操作日志重复消息跳过: msgId={}", message.getMsgId());
            return;
        }
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
        operationLogMapperService.save(entity);
        log.debug("操作日志已落库: msgId={}, operation={}", message.getMsgId(), message.getOperation());
    }
}
