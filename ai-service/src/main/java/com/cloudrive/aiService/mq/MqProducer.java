package com.cloudrive.aiService.mq;

import com.cloudrive.pan_common.constant.MqNames;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.domain.mq.PgIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ 生产端（AI 服务）
 * <p>
 * Agent 的删除 / 恢复动作会写 {@code t_file_info}，与 file-manage-service 是同一张表的
 * 两个写入方，因此也必须发出同一套旁路消息，否则会出现「文件已经删了但搜得到、
 * 向量库里还留着」的错位——用户在 AI 侧删掉的文件，ES 检索仍然返回。
 * </p>
 * <p>
 * 发送失败只记日志：索引属于旁路能力，不能反过来让文件操作失败，
 * 后续由索引重建任务补偿。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendIndexInsert(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.INSERT_KEY, message, "ES插入");
    }

    public void sendIndexDelete(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.DELETE_KEY, message, "ES删除");
    }

    public void sendIndexUpdate(FileIndexMessage message) {
        send(MqNames.Es.EXCHANGE, MqNames.Es.UPDATE_KEY, message, "ES更新");
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

    private void send(String exchange, String routingKey, Object message, String type) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("发送{}消息成功: {}", type, routingKey);
        } catch (Exception e) {
            log.warn("发送{}消息失败（不阻塞主流程）: exchange={}, routingKey={}", type, exchange, routingKey, e);
        }
    }
}
