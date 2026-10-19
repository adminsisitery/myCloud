package com.cloudrive.fileAsyncService.mq.producer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * MQ 生产端实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMqServiceImpl implements RabbitMqService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void sendInsertMessage(FileIndexMessage message) {
        rabbitTemplate.convertAndSend(MqConstants.Es.EXCHANGE, MqConstants.Es.INSERT_KEY, message);
        log.debug("发送 ES 插入消息: fileId={}", message == null ? null : message.getId());
    }
}
