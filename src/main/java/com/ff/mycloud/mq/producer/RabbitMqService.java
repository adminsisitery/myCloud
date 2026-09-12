package com.ff.mycloud.mq.producer;

import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.model.entity.PgSender;
import com.ff.mycloud.model.entity.UsageMessage;

/**
 * RabbitMQ消息发送服务接口
 *
 * 定义文件索引同步和操作日志的消息发送方法。
 * 通过RabbitMQ将ES索引同步和日志写入操作异步解耦，提高主业务响应速度。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
public interface RabbitMqService {

    /**
     * 发送文件新增索引消息
     *
     * @param fileDocument 文件文档对象，包含待索引的文件信息
     */
    void sendInsertMessage(FileDocument fileDocument);

    /**
     * 发送文件删除索引消息
     *
     * @param fileDocument 文件文档对象，包含待删除索引的文件ID
     */
    void sendDeleteMessage(FileDocument fileDocument);

    /**
     * 发送文件重命名索引更新消息
     *
     * @param fileDocument 文件文档对象，包含待更新索引的文件ID和新的文件名
     */
    void sendUpdateMessage(FileDocument fileDocument);

    /**
     * 发送操作日志消息
     *
     * 消息发送前会自动生成唯一msgId并设置到日志对象中，用于消费者端幂等去重。
     *
     * @param operationLog 操作日志实体，包含操作类型、用户ID、目标文件等信息
     */
    void sendLogMessage(OperationLog operationLog);

    /**
     * 发送文件新增pg向量
     * @param segments
     */

    void sendInsertPG(PgSender pgs);

    void sendDeletePG(PgSender pgs);

    void UsageChanged(UsageMessage usageMessage);

    void sendFolderDownloadTask(String taskId);

}
