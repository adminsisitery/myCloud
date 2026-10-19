package com.cloudrive.fileDownloadService.mq;

import com.cloudrive.fileDownloadService.common.FolderDownloadConstants;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 文件夹打包任务消息生产端
 * <p>
 * 只负责把 taskId 投递到 folder.download.topic，真正的 ZIP 打包由
 * file-async-service 的消费者执行（共享 Redis 中的任务状态与 OSS 临时对象）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Component
public class FolderDownloadMqProducer {

    private final RabbitTemplate rabbitTemplate;

    public FolderDownloadMqProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 投递文件夹打包任务
     *
     * @param taskId 任务ID
     */
    public void sendFolderDownloadTask(String taskId) {
        rabbitTemplate.convertAndSend(
                FolderDownloadConstants.EXCHANGE,
                FolderDownloadConstants.ROUTING_KEY,
                taskId);
    }
}
