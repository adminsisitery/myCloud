package com.ff.mycloud.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import com.ff.mycloud.common.constant.ESConstants;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 文件索引同步消费者
 *
 * 消费 RabbitMQ 消息，异步维护 ES 文件索引，实现最终一致性。
 * 监听三个队列分别处理文件的新增、删除、重命名索引同步。
 * 队列声明统一在 {@link com.ff.mycloud.mq.config.RabbitMQConfig}，均配置 DLX。
 *
 * 幂等说明：以下三个操作均为天然幂等，无需额外去重
 *   INSERT → ES save() 内部 upsert，同 ID 写两次结果一致
 *   DELETE → ES deleteById() 删除不存在记录不会报错（no-op）
 *   UPDATE → renameById findById + save，改成同一名字结果一致
 *
 * @author 飞飞
 * @date 2026/3/14
 */
@Component
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
@Slf4j
public class ESSyncConsumer {

    private final FileSearchService fileSearchService;
    private final com.ff.mycloud.service.FileInfoMapperService files;
    private final org.redisson.api.RedissonClient redis;

    public ESSyncConsumer(FileSearchService fileSearchService, com.ff.mycloud.service.FileInfoMapperService files,
                          org.redisson.api.RedissonClient redis) {
        this.fileSearchService = fileSearchService;
        this.files = files;
        this.redis = redis;
    }

    /** 文件上传/秒传 → 写入ES索引 */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = ESConstants.QUEUE.INSERT_QUEUE)
    public void esInsert(FileDocument message) {
        log.debug("消息消费开始: operation=esInsert, businessId={}", message.getId());
        synchronize(message, false);
    }

    /** 文件删除 → 从ES索引中删除 */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = ESConstants.QUEUE.DELETE_QUEUE)
    public void esDelete(FileDocument message) {
        log.debug("消息消费开始: operation=esDelete, businessId={}", message.getId());
        synchronize(message, true);
    }

    /** 文件重命名 → 更新ES索引中的文件名 */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 3000))
    @RabbitListener(queues = ESConstants.QUEUE.UPDATE_QUEUE)
    public void esUpdate(FileDocument message) {
        log.debug("消息消费开始: operation=esUpdate, businessId={}", message.getId());
        synchronize(message, false);
    }

    private void synchronize(FileDocument message, boolean deletion) {
        var lock = redis.getLock("file-index:" + message.getId());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) throw new IllegalStateException("索引繁忙，请重试");
            var current = files.getById(message.getId());
            if (current == null || Boolean.TRUE.equals(current.getIsDeleted())) {
                fileSearchService.del(message.getId());
                return;
            }
            if (deletion) return;
            if (message.getUserId() != null && !java.util.Objects.equals(message.getUserId(), current.getUserId())) return;
            message.setUserId(current.getUserId());
            message.setFileName(current.getFilename() + (Boolean.TRUE.equals(current.getIsFolder()) ? "" : "." + java.util.Objects.toString(current.getFileSuffix(), "")));
            fileSearchService.save(message);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("索引任务已中断", error);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
