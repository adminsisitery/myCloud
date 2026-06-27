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
 *
 * 异步消费RabbitMQ中的操作日志消息并写入数据库，不阻塞主业务流程。
 *
 * 幂等策略：Redis SETNX 去重
 * 每条日志消息携带唯一 msgId，消费前通过{@link MessageDedupService}执行SETNX判断是否已处理，
 * 避免MQ重复投递导致日志重复写入。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
public class OperationLogConsumer {

    private final OperationLogMapperService operationLogMapperService;
    private final MessageDedupService messageDedupService;

    /**
     * 构造方法注入操作日志服务和消息去重服务
     *
     * @param operationLogMapperService 操作日志数据库操作服务
     * @param messageDedupService       消息去重服务，基于Redis SETNX实现消费幂等
     */
    public OperationLogConsumer(OperationLogMapperService operationLogMapperService,
                                MessageDedupService messageDedupService) {
        this.operationLogMapperService = operationLogMapperService;
        this.messageDedupService = messageDedupService;
    }

    /**
     * 处理操作日志消息
     *
     * 监听日志队列，接收到操作日志消息后先通过Redis SETNX进行去重判断：
     * 如果消息已被消费过则直接跳过，否则写入操作日志数据库表。
     * 去重窗口为24小时，超过此窗口的消息视为新消息处理。
     *
     * @param message 操作日志实体，包含操作类型、用户ID、目标文件及唯一msgId
     */
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
