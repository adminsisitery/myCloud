package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.service.FolderDownloadTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 文件夹打包消费者（从单体 FolderDownloadConsumer 迁入）
 * <p>
 * 队列名与单体一致，任务状态与 OSS 对象路径沿用共享 Redis / OSS 约定。
 * 过渡期单体的创建/查询/取消接口保持不变，仅消费端由本服务接管。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FolderDownloadConsumer {

    private final FolderDownloadTaskService folderDownloadTaskService;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000, multiplier = 2), retryFor = Exception.class)
    @RabbitListener(queues = MqConstants.FolderDownload.QUEUE, concurrency = "1-2")
    public void packageFolder(String taskId) {
        log.info("收到文件夹打包任务: taskId={}", taskId);
        folderDownloadTaskService.process(taskId);
    }
}
