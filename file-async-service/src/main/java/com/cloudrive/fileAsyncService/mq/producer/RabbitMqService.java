package com.cloudrive.fileAsyncService.mq.producer;

import com.cloudrive.pan_common.domain.mq.FileIndexMessage;

/**
 * MQ 生产端（异步服务只需要回投 ES 插入消息做索引重建）
 */
public interface RabbitMqService {

    void sendInsertMessage(FileIndexMessage message);
}
