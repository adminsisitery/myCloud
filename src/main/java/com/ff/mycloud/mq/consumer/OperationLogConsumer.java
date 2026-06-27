package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.service.OperationLogMapperService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者
 * 异步写入操作日志表，不阻塞主业务
 *
 * 幂等策略：Redis SETNX 去重
 * 每条日志消息携带唯一 msgId，消费前 SETNX 判断是否已处理，
 * 避免 MQ 重复投递导致日志重复写入
 */
@Component
public class OperationLogConsumer {

    private final OperationLogMapperService operationLogMapperService;
    private final MessageDedupService messageDedupService;

    public OperationLogConsumer(OperationLogMapperService operationLogMapperService,
                                MessageDedupService messageDedupService) {
        this.operationLogMapperService = operationLogMapperService;
        this.messageDedupService = messageDedupService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(CommonConstants.LogMQ.QUEUE),
                    exchange = @Exchange(CommonConstants.LogMQ.EXCHANGE),
                    key = CommonConstants.LogMQ.KEY
            )
    )
    public void handleLog(OperationLog message) {
        // SETNX 去重：已经消费过的消息直接跳过
        if (!messageDedupService.tryConsume(message.getMsgId())) {
            return;
        }
        operationLogMapperService.save(message);
    }
}
