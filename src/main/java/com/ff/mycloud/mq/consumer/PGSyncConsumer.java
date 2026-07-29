package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.model.entity.PgSender;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Component
@ConditionalOnBean(EmbeddingStore.class)
public class PGSyncConsumer {

    private final EmbeddingStore embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;

    public PGSyncConsumer(EmbeddingStore embeddingStore, DocumentSplitter documentSplitter, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.documentSplitter = documentSplitter;
        this.embeddingModel = embeddingModel;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(CommonConstants.PG.INSERT_QUEUE),
                    exchange = @Exchange(CommonConstants.PG.TOPIC_EXCHANGE),
                    key = CommonConstants.PG.INSERT_KEY
            )
    )
    public void pgInsert(PgSender message) {
        String text = message.getText();
        if (text == null || text.isBlank()) return;

        Document document = Document.from(text);
        List<TextSegment> segments = documentSplitter.split(document);
        segments.forEach(s -> s.metadata()
                .put("fileId", String.valueOf(message.getFileId()))
                .put("userId", String.valueOf(message.getUserId())));

        // 先 embedding，再存入向量库
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(CommonConstants.PG.DELETE_QUEUE),
                    exchange = @Exchange(CommonConstants.PG.TOPIC_EXCHANGE),
                    key = CommonConstants.PG.DELETE_KEY
            )
    )
    public void pgDelete(PgSender message) {
        embeddingStore.removeAll(
                metadataKey("fileId").isEqualTo(String.valueOf(message.getFileId()))
        );
    }
}
