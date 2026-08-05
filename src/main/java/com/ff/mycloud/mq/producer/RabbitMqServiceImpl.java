package com.ff.mycloud.mq.producer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.constant.ESConstants;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.model.entity.PgSender;
import com.ff.mycloud.model.entity.UsageMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * RabbitMQ消息发送服务实现类
 *
 * 使用RabbitTemplate将文件索引同步消息和操作日志消息发送到对应的交换机和队列。
 * 通过Topic Exchange + Routing Key实现消息路由，消费者根据Routing Key绑定不同队列处理。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Service
public class RabbitMqServiceImpl implements RabbitMqService {

    RabbitTemplate rabbitTemplate;

    /**
     * 构造方法注入RabbitTemplate
     *
     * @param rabbitTemplate Spring AMQP提供的RabbitMQ操作模板
     */
    public RabbitMqServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送文件新增索引消息到ES同步交换机
     *
     * 使用INSERT_KEY路由键，消费者{@link com.ff.mycloud.mq.consumer.ESSyncConsumer#esInsert}处理此消息。
     *
     * @param fileDocument 文件文档对象，包含待索引的文件信息
     */
    @Override
    public void sendInsertMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE, ESConstants.KEY.INSERT_KEY, fileDocument);
    }

    /**
     * 发送文件删除索引消息到ES同步交换机
     *
     * 使用DELETE_KEY路由键，消费者{@link com.ff.mycloud.mq.consumer.ESSyncConsumer#esDelete}处理此消息。
     *
     * @param fileDocument 文件文档对象，包含待删除索引的文件ID
     */
    @Override
    public void sendDeleteMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE, ESConstants.KEY.DELETE_KEY, fileDocument);
    }

    /**
     * 发送文件重命名索引更新消息到ES同步交换机
     *
     * 使用UPDATE_KEY路由键，消费者{@link com.ff.mycloud.mq.consumer.ESSyncConsumer#esUpdate}处理此消息。
     *
     * @param fileDocument 文件文档对象，包含待更新索引的文件ID和新的文件名
     */
    @Override
    public void sendUpdateMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE, ESConstants.KEY.UPDATE_KEY, fileDocument);
    }

    /**
     * 发送操作日志消息到日志交换机
     *
     * 发送前自动生成UUID作为消息唯一标识（msgId），设置到日志对象中。
     * 消费者{@link com.ff.mycloud.mq.consumer.OperationLogConsumer#handleLog}通过
     * {@link com.ff.mycloud.mq.consumer.MessageDedupService#tryConsume}进行SETNX去重判断，
     * 避免MQ重复投递导致日志重复写入。
     *
     * @param operationLog 操作日志实体，msgId将在发送前自动填充
     */
    @Override
    public void sendLogMessage(OperationLog operationLog) {
        // 生成消息唯一ID，用于消费者幂等去重
        operationLog.setMsgId(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(CommonConstants.LogMQ.EXCHANGE,
                CommonConstants.LogMQ.KEY, operationLog);
    }


    /**
     * 发送文件新增索引消息到PG同步交换机
     *
     * 使用INSERT_KEY路由键，消费者{@link com.ff.mycloud.mq.consumer.PGSyncConsumer}处理此消息。
     *
     * @param segments 文件文档对象，包含待索引的文件信息
     * @param segments
     */
    @Override
    public void sendInsertPG(PgSender pgs) {
        rabbitTemplate.convertAndSend(CommonConstants.PG.TOPIC_EXCHANGE,CommonConstants.PG.INSERT_KEY, pgs);
    }

    @Override
    public void sendDeletePG(PgSender pgs) {
        rabbitTemplate.convertAndSend(CommonConstants.PG.TOPIC_EXCHANGE, CommonConstants.PG.DELETE_KEY, pgs);
    }

    @Override
    public void UsageChanged(UsageMessage usageMessage) {
        rabbitTemplate.convertAndSend(CommonConstants.USAGE.USAGE_EXCHANGE,CommonConstants.USAGE.USAGE_KEY,usageMessage);
    }
}
