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
 */
@Component
public class OperationLogConsumer {

    private final OperationLogMapperService operationLogMapperService;

    public OperationLogConsumer(OperationLogMapperService operationLogMapperService) {
        this.operationLogMapperService = operationLogMapperService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(CommonConstants.LogMQ.QUEUE),
                    exchange = @Exchange(CommonConstants.LogMQ.EXCHANGE),
                    key = CommonConstants.LogMQ.KEY
            )
    )
    public void handleLog(OperationLog message) {
        operationLogMapperService.save(message);
    }
}
