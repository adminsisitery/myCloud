package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.service.FolderDownloadTaskService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Component
public class FolderDownloadConsumer {
    private final FolderDownloadTaskService service;

    public FolderDownloadConsumer(FolderDownloadTaskService service) {
        this.service = service;
    }

    /** 独立低并发消费者，避免多个大目录同时打包耗尽磁盘和 OSS 连接。 */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000, multiplier = 2))
    @RabbitListener(queues = CommonConstants.FolderDownloadMQ.QUEUE, concurrency = "1-2")
    public void packageFolder(String taskId) {
        service.process(taskId);
    }
}
