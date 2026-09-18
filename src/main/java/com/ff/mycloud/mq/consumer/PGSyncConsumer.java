package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.PgSender;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import dev.langchain4j.data.document.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.retry.annotation.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/** Serialized replacement makes redelivery idempotent; live DB state rejects stale delete/insert events. */
@Component
@ConditionalOnBean(EmbeddingStore.class)
public class PGSyncConsumer {
    private final EmbeddingStore<TextSegment> vectors;
    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddings;
    private final FileInfoMapperService files;
    private final RedissonClient redis;
    private final FileIndexRebuilder rebuilder;

    public PGSyncConsumer(EmbeddingStore<TextSegment> vectors, DocumentSplitter splitter, EmbeddingModel embeddings,
                          FileInfoMapperService files, RedissonClient redis, FileIndexRebuilder rebuilder) {
        this.vectors = vectors;
        this.splitter = splitter;
        this.embeddings = embeddings;
        this.files = files;
        this.redis = redis;
        this.rebuilder = rebuilder;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = CommonConstants.PG.INSERT_QUEUE)
    public void pgInsert(PgSender message) {
        locked(message, () -> {
            FileInfo file = files.getById(message.getFileId());
            if (!ownedLive(file, message) || Boolean.TRUE.equals(file.getIsFolder())) return;
            if (message.getContentHash() != null && !Objects.equals(message.getContentHash(), file.getSha256Hash())) return;
            String text = message.isRebuild() ? rebuilder.extract(file) : message.getText();
            if (text == null) throw new IllegalArgumentException("索引文本缺失");
            var segments = text.isBlank() ? List.<TextSegment>of() : splitter.split(Document.from(text));
            segments.forEach(s -> s.metadata().put("fileId", file.getId().toString()).put("userId", file.getUserId().toString())
                    .put("fileName", file.getFilename() + "." + Objects.toString(file.getFileSuffix(), "")));
            var embedded = segments.isEmpty() ? List.<dev.langchain4j.data.embedding.Embedding>of() : embeddings.embedAll(segments).content();
            FileInfo current = files.getById(file.getId());
            if (!ownedLive(current, message) || !Objects.equals(current.getSha256Hash(), file.getSha256Hash())) return;
            vectors.removeAll(scope(message));
            if (!segments.isEmpty()) {
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < segments.size(); i++) ids.add(UUID.nameUUIDFromBytes(
                        (file.getUserId() + ":" + file.getId() + ":" + i + ":" + segments.get(i).text()).getBytes(StandardCharsets.UTF_8)).toString());
                vectors.addAll(ids, embedded, segments);
            }
            if (message.isRebuild()) rebuilder.restoreKeywords(current, text);
        });
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = CommonConstants.PG.DELETE_QUEUE)
    public void pgDelete(PgSender message) {
        locked(message, () -> {
            FileInfo current = files.getById(message.getFileId());
            if (ownedLive(current, message)) return; // A delayed deletion must not erase a restored document.
            vectors.removeAll(scope(message));
        });
    }

    private boolean ownedLive(FileInfo file, PgSender message) {
        return file != null && Objects.equals(file.getUserId(), message.getUserId()) && !Boolean.TRUE.equals(file.getIsDeleted());
    }

    private dev.langchain4j.store.embedding.filter.Filter scope(PgSender message) {
        return metadataKey("fileId").isEqualTo(message.getFileId().toString())
                .and(metadataKey("userId").isEqualTo(message.getUserId().toString()));
    }

    private void locked(PgSender message, Runnable operation) {
        if (message.getFileId() == null || message.getUserId() == null) throw new IllegalArgumentException("索引身份缺失");
        var lock = redis.getLock("file-index:" + message.getFileId());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, TimeUnit.SECONDS);
            if (!acquired) throw new IllegalStateException("索引繁忙，请重试");
            operation.run();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("索引任务已中断", error);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
