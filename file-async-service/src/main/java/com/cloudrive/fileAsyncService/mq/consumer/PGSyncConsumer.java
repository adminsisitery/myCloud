package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.model.entity.PgSender;
import com.cloudrive.fileAsyncService.service.FileIndexRebuilder;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * PG 向量同步消费者（移植单体 PGSyncConsumer）
 * <p>
 * 插入：以 DB 实时数据为准做归属/删除/内容 hash 校验，删除过期向量后重建；
 * 删除：延迟删除消息到达时若文件仍有效（例如回收站已恢复），跳过删除。
 * 重试耗尽后由 Broker 的 DLX policy 转入死信队列。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnBean(EmbeddingStore.class)
@RequiredArgsConstructor
public class PGSyncConsumer {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final FileInfoMapperService files;
    private final RedissonClient redisClient;
    private final FileIndexRebuilder indexRebuilder;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = MqConstants.Pg.INSERT_QUEUE)
    public void handleInsert(PgSender message) {
        if (message == null) {
            return;
        }
        withLock(message, () -> {
            FileInfo file = files.getById(message.getFileId());
            if (!ownedLive(file, message)) {
                log.info("文件已删除或归属变化，跳过向量索引: fileId={}", message.getFileId());
                return;
            }
            if (Boolean.TRUE.equals(file.getIsFolder())) {
                return;
            }
            if (message.getContentHash() != null && file.getSha256Hash() != null
                    && !message.getContentHash().equals(file.getSha256Hash())) {
                log.info("文件内容已变化，跳过过期向量消息: fileId={}", message.getFileId());
                return;
            }

            String text = message.isRebuild()
                    ? indexRebuilder.extract(file)
                    : message.getText();
            if (text == null) {
                throw new IllegalArgumentException("索引文本缺失");
            }
            List<TextSegment> segments = text.isBlank()
                    ? List.of()
                    : documentSplitter.split(Document.from(text));
            List<Embedding> embeddings = segments.isEmpty()
                    ? List.of()
                    : embeddingModel.embedAll(segments).content();

            // 长耗时嵌入后重新确认文件仍然有效且内容未变，避免覆盖新数据的索引
            FileInfo current = files.getById(message.getFileId());
            if (!ownedLive(current, message)) {
                return;
            }
            if (message.getContentHash() != null && current.getSha256Hash() != null
                    && !message.getContentHash().equals(current.getSha256Hash())) {
                return;
            }
            String fileName = current.getFilename()
                    + (current.getFileSuffix() == null || current.getFileSuffix().isBlank()
                    ? ""
                    : "." + current.getFileSuffix());
            String fileIdText = String.valueOf(message.getFileId());
            String userIdText = String.valueOf(message.getUserId());
            for (TextSegment segment : segments) {
                segment.metadata().put("fileId", fileIdText);
                segment.metadata().put("userId", userIdText);
                segment.metadata().put("fileName", fileName);
            }

            embeddingStore.removeAll(scope(message.getFileId(), message.getUserId()));
            embeddingStore.addAll(embeddings, segments);
            if (message.isRebuild()) {
                indexRebuilder.restoreKeywords(current, text);
            }
            log.debug("PG 向量已同步: fileId={}, segments={}", message.getFileId(), segments.size());
        });
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = MqConstants.Pg.DELETE_QUEUE)
    public void handleDelete(PgSender message) {
        if (message == null) {
            return;
        }
        withLock(message, () -> {
            FileInfo current = files.getById(message.getFileId());
            if (ownedLive(current, message)) {
                // 文件在删除消息消费前已恢复（例如回收站还原），不能删除它的向量
                log.info("文件仍有效，跳过延迟向量删除: fileId={}", message.getFileId());
                return;
            }
            embeddingStore.removeAll(scope(message.getFileId(), message.getUserId()));
            log.debug("PG 向量已删除: fileId={}", message.getFileId());
        });
    }

    private void withLock(PgSender message, Runnable action) {
        if (message.getFileId() == null || message.getUserId() == null) {
            throw new IllegalArgumentException("索引身份缺失");
        }
        RLock lock = redisClient.getLock(MqConstants.INDEX_LOCK_PREFIX + message.getFileId());
        boolean locked = false;
        try {
            locked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("索引繁忙，请重试");
            }
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("索引任务已中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean ownedLive(FileInfo file, PgSender message) {
        return file != null
                && !Boolean.TRUE.equals(file.getIsDeleted())
                && message.getUserId() != null
                && message.getUserId().equals(file.getUserId());
    }

    private Filter scope(Long fileId, Long userId) {
        return metadataKey("fileId").isEqualTo(String.valueOf(fileId))
                .and(metadataKey("userId").isEqualTo(String.valueOf(userId)));
    }
}
