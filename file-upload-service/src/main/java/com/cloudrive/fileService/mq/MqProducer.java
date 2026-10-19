package com.cloudrive.fileService.mq;

import com.cloudrive.pan_common.constant.MqNames;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.domain.mq.PgIndexMessage;
import com.cloudrive.pan_common.domain.mq.UsageMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ 生产端（上传服务）
 * <p>
 * 所有发送都吞掉异常只记日志：消息属于旁路能力（ES/PG 索引、操作日志、用量提醒），
 * 发送失败不能影响上传主流程，后续可通过补偿任务重建索引。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendIndexInsert(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.INSERT_KEY, message, "ES插入");
    }

    public void sendIndexUpdate(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.UPDATE_KEY, message, "ES更新");
    }

    public void sendIndexDelete(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.DELETE_KEY, message, "ES删除");
    }

    public void sendPgInsert(PgIndexMessage message) {
        send(MqNames.Pg.EXCHANGE, MqNames.Pg.INSERT_KEY, message, "PG插入");
    }

    public void sendPgDelete(PgIndexMessage message) {
        send(MqNames.Pg.EXCHANGE, MqNames.Pg.DELETE_KEY, message, "PG删除");
    }

    public void sendLog(OperationLogMessage message) {
        send(MqNames.Log.EXCHANGE, MqNames.Log.KEY, message, "操作日志");
    }

    public void sendUsage(UsageMessage message) {
        send(MqNames.Usage.EXCHANGE, MqNames.Usage.KEY, message, "用量通知");
    }

    private void send(String exchange, String routingKey, Object message, String type) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("发送{}消息成功: {}", type, routingKey);
        } catch (Exception e) {
            log.warn("发送{}消息失败（不阻塞主流程）: exchange={}, routingKey={}", type, exchange, routingKey, e);
        }
    }
}
