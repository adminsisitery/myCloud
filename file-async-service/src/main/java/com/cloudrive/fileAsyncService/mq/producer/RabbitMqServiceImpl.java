package com.cloudrive.fileAsyncService.mq.producer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.FileDocument;
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
    public void sendInsertMessage(FileDocument document) {
        rabbitTemplate.convertAndSend(MqConstants.Es.EXCHANGE, MqConstants.Es.INSERT_KEY, document);
        log.debug("发送 ES 插入消息: fileId={}", document == null ? null : document.getId());
    }
}
