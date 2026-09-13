package com.ff.mycloud.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.PgSender;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * PG 向量索引同步消费者
 * <p>队列声明统一在 {@link com.ff.mycloud.mq.config.RabbitMQConfig}，均配置 DLX。</p>
 */
@Component
@ConditionalOnBean(EmbeddingStore.class)
@Slf4j
public class PGSyncConsumer {

    private final EmbeddingStore embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;

    public PGSyncConsumer(EmbeddingStore embeddingStore, DocumentSplitter documentSplitter, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.documentSplitter = documentSplitter;
        this.embeddingModel = embeddingModel;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = CommonConstants.PG.INSERT_QUEUE)
    public void pgInsert(PgSender message) {
        log.debug("消息消费开始: operation=pgInsert, businessId={}", message.getFileId());
        String text = message.getText();
        if (text == null || text.isBlank()) return;

        Document document = Document.from(text);
        List<TextSegment> segments = documentSplitter.split(document);
        segments.forEach(s -> s.metadata()
                .put("fileId", String.valueOf(message.getFileId()))
                .put("userId", String.valueOf(message.getUserId())));

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 5000))
    @RabbitListener(queues = CommonConstants.PG.DELETE_QUEUE)
    public void pgDelete(PgSender message) {
        log.debug("消息消费开始: operation=pgDelete, businessId={}", message.getFileId());
        embeddingStore.removeAll(
                metadataKey("fileId").isEqualTo(String.valueOf(message.getFileId()))
        );
    }
}
