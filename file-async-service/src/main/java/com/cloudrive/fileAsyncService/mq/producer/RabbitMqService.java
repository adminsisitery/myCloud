package com.cloudrive.fileAsyncService.mq.producer;

import com.cloudrive.fileAsyncService.model.entity.FileDocument;

/**
 * MQ 生产端（异步服务只需要回投 ES 插入消息做索引重建）
 */
public interface RabbitMqService {

    void sendInsertMessage(FileDocument document);
}
