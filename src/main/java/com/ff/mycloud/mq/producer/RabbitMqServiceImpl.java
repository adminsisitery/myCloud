package com.ff.mycloud.mq.producer;

import com.ff.mycloud.common.constant.ESConstants;
import com.ff.mycloud.model.entity.FileDocument;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMqServiceImpl implements RabbitMqService {

    RabbitTemplate rabbitTemplate;

    public RabbitMqServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendInsertMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE,ESConstants.KEY.INSERT_KEY, fileDocument);
    }

    @Override
    public void sendDeleteMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE,ESConstants.KEY.DELETE_KEY, fileDocument);
    }

    @Override
    public void sendUpdateMessage(FileDocument fileDocument) {
        rabbitTemplate.convertAndSend(ESConstants.EXCHANGE.TOPIC_EXCHANGE,ESConstants.KEY.UPDATE_KEY, fileDocument);
    }
}
