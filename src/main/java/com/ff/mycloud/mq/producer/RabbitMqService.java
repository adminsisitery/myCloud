package com.ff.mycloud.mq.producer;

import com.ff.mycloud.model.entity.FileDocument;

public interface RabbitMqService {
    void sendInsertMessage(FileDocument fileDocument);
    void sendDeleteMessage(FileDocument fileDocument);
    void sendUpdateMessage(FileDocument fileDocument);
}
