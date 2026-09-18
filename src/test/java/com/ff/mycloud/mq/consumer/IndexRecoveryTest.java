package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.model.entity.*;
import com.ff.mycloud.service.*;
import dev.langchain4j.data.document.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.redisson.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndexRecoveryTest {
    EmbeddingStore<TextSegment> vectors;
    DocumentSplitter splitter;
    EmbeddingModel embeddings;
    FileInfoMapperService files;
    RedissonClient redis;
    FileIndexRebuilder rebuilder;
    PGSyncConsumer consumer;
    FileInfo file;
    PgSender message;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        vectors = mock(EmbeddingStore.class); splitter = mock(DocumentSplitter.class); embeddings = mock(EmbeddingModel.class);
        files = mock(FileInfoMapperService.class); redis = mock(RedissonClient.class); rebuilder = mock(FileIndexRebuilder.class);
        RLock lock = mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        file = new FileInfo(); file.setId(7L); file.setUserId(1L); file.setFilename("合同"); file.setFileSuffix("txt"); file.setSha256Hash("v1"); file.setIsDeleted(false);
        when(files.getById(7L)).thenReturn(file);
        when(splitter.split(any(Document.class))).thenAnswer(i -> List.of(TextSegment.from("合同正文")));
        when(embeddings.embedAll(anyList())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1,0}))));
        message = PgSender.builder().fileId(7L).userId(1L).contentHash("v1").text("合同正文").build();
        consumer = new PGSyncConsumer(vectors, splitter, embeddings, files, redis, rebuilder);
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void redeliveryReplacesScopedVectorsUsingStableIds() {
        consumer.pgInsert(message); consumer.pgInsert(message);
        ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass(List.class);
        verify(vectors, times(2)).addAll(ids.capture(), anyList(), anyList());
        assertEquals(ids.getAllValues().getFirst(), ids.getAllValues().getLast());
        verify(vectors, times(2)).removeAll(any(dev.langchain4j.store.embedding.filter.Filter.class));
    }
    @Test void delayedDeletionDoesNotEraseRestoredFile() {
        consumer.pgDelete(message);
        verifyNoInteractions(vectors);
        file.setIsDeleted(true);
        consumer.pgDelete(message);
        verify(vectors).removeAll(any(dev.langchain4j.store.embedding.filter.Filter.class));
    }
    @Test void deletedOrChangedFileDoesNotGetAnOldIndex() {
        file.setIsDeleted(true); consumer.pgInsert(message);
        file.setIsDeleted(false); file.setSha256Hash("v2"); consumer.pgInsert(message);
        verifyNoInteractions(vectors, embeddings);
    }
    @Test void restoreExtractsOriginalTextAndRebuildsBothIndexes() {
        message.setRebuild(true); message.setText(null);
        when(rebuilder.extract(file)).thenReturn("合同正文");
        consumer.pgInsert(message);
        verify(rebuilder).extract(file);
        verify(rebuilder).restoreKeywords(file, "合同正文");
        verify(vectors).addAll(anyList(), anyList(), anyList());
    }
    @Test void failedEmbeddingDoesNotDeleteExistingVectors() {
        when(embeddings.embedAll(anyList())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> consumer.pgInsert(message));
        verifyNoInteractions(vectors);
    }
    @Test void delayedEsDeletionAndInsertionFollowCurrentDatabaseState() {
        var search = mock(FileSearchService.class);
        var es = new ESSyncConsumer(search, files, redis);
        var document = new FileDocument(); document.setId(7L); document.setUserId(1L);
        es.esDelete(document); verifyNoInteractions(search);
        file.setIsDeleted(true); es.esInsert(document);
        verify(search).del(7L); verify(search, never()).save(any());
    }
}
