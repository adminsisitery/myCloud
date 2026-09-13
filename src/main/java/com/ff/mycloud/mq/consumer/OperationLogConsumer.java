package com.ff.mycloud.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.service.OperationLogMapperService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者
 *
 * 异步消费RabbitMQ中的操作日志消息并写入数据库，不阻塞主业务流程。
 * 队列声明统一在 {@link com.ff.mycloud.mq.config.RabbitMQConfig}，均配置 DLX。
 *
 * 幂等策略：Redis SETNX 去重
 * 每条日志消息携带唯一 msgId，消费前通过{@link MessageDedupService}执行SETNX判断是否已处理，
 * 避免MQ重复投递导致日志重复写入。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
@Slf4j
public class OperationLogConsumer {

    private final OperationLogMapperService operationLogMapperService;
    private final MessageDedupService messageDedupService;

    public OperationLogConsumer(OperationLogMapperService operationLogMapperService,
                                MessageDedupService messageDedupService) {
        this.operationLogMapperService = operationLogMapperService;
        this.messageDedupService = messageDedupService;
    }

    /**
     * 处理操作日志消息
     *
     * SETNX 去重：已经消费过的消息直接跳过，去重窗口24小时。
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = CommonConstants.LogMQ.QUEUE)
    public void handleLog(OperationLog message) {
        log.debug("消息消费开始: operation=handleLog, businessId={}", message.getMsgId());
        if (!messageDedupService.tryConsume(message.getMsgId())) {
            log.debug("操作日志重复消息跳过: msgId={}", message.getMsgId());
            return;
        }
        operationLogMapperService.save(message);
        log.debug("操作日志消费返回: msgId={}, userId={}, fileId={}", message.getMsgId(), message.getUserId(), message.getFileId());
    }
}
