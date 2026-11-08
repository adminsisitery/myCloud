package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.pan_common.constant.MqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 死信自动重投（默认关闭，{@code pan.rabbit.dlq.auto-requeue=true} 开启）。
 * <p>
 * 消费 {@code dlx:queue}，按消息 {@code x-death} 头里的来源队列把消息发回原交换机与路由键。
 * 重投次数超过上限后不再自动处理，只打 error 日志把消息体原样打出来，留给人工恢复。
 * </p>
 * <p>
 * 关闭是刻意的默认值：自动重投是毒消息无限循环的源头。人工介入走 RabbitMQ 管理台的
 * Move messages，或参考 {@code deploy/scripts/dlq-requeue.md}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pan.rabbit.dlq", name = "auto-requeue", havingValue = "true")
public class DlqRequeueListener {

    /**
     * 超过该次数不再重投。{@code x-death.count} 在消息每次重新死信时累加。
     */
    private static final long MAX_REQUEUE_TIMES = 3L;

    /**
     * 来源队列 → [交换机, 路由键]
     */
    private static final Map<String, String[]> ROUTES = Map.of(
            MqNames.Log.QUEUE, new String[]{MqNames.Log.EXCHANGE, MqNames.Log.KEY},
            MqNames.Usage.QUEUE, new String[]{MqNames.Usage.EXCHANGE, MqNames.Usage.KEY},
            MqNames.FolderDownload.QUEUE, new String[]{MqNames.FolderDownload.EXCHANGE, MqNames.FolderDownload.KEY}
    );

    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqNames.Dlq.QUEUE)
    public void requeue(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) {
            log.error("死信缺少 x-death 头，无法判定来源，需人工处理 body={}",
                    new String(message.getBody(), StandardCharsets.UTF_8));
            return;
        }

        Map<String, ?> death = xDeath.get(0);
        String originQueue = String.valueOf(death.get("queue"));
        long count = death.get("count") instanceof Number number ? number.longValue() : 0L;

        if (count > MAX_REQUEUE_TIMES) {
            log.error("死信已重投 {} 次仍失败，停止自动重投，需人工处理 queue={} body={}",
                    count, originQueue, new String(message.getBody(), StandardCharsets.UTF_8));
            return;
        }

        String[] route = ROUTES.get(originQueue);
        if (route == null) {
            log.error("未知来源队列，无法自动重投 queue={} body={}",
                    originQueue, new String(message.getBody(), StandardCharsets.UTF_8));
            return;
        }

        try {
            rabbitTemplate.send(route[0], route[1], message);
            log.warn("死信已重投 queue={} exchange={} routingKey={} 第 {} 次",
                    originQueue, route[0], route[1], count + 1);
        } catch (Exception e) {
            // 重投本身失败（broker 不可达等），消息体打进日志供人工恢复，避免在 DLQ 里死循环
            log.error("死信重投失败，需人工处理 queue={} body={}",
                    originQueue, new String(message.getBody(), StandardCharsets.UTF_8), e);
        }
    }
}
