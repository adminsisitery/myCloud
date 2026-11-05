package com.cloudrive.fileAsyncService.mq.consumer;

import com.cloudrive.fileAsyncService.common.MqConstants;
import com.cloudrive.fileAsyncService.model.entity.FileDocument;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import com.cloudrive.fileAsyncService.service.FileSearchService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ESSyncConsumer 单元测试：ES 索引同步。
 * <p>
 * 对应 TEST-CASES.md 的 TC-AS-001 ~ TC-AS-008。
 * 这个消费者没有 HTTP 入口，只能靠单测。核心规则是「消息只是通知，一切以 MySQL 实时状态为准」：
 * 文件删了要删索引、消息说删但文件还在就不能动、归属对不上要跳过。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ESSyncConsumerTest {

    private static final Long FILE_ID = 100L;
    private static final Long USER_ID = 7L;

    @Mock FileSearchService fileSearchService;
    @Mock FileInfoMapperService files;
    @Mock RedissonClient redisClient;
    @Mock RLock lock;

    @InjectMocks ESSyncConsumer consumer;

    private FileIndexMessage message(Long id, Long userId) {
        FileIndexMessage m = new FileIndexMessage();
        m.setId(id);
        m.setUserId(userId);
        m.setFileType("text/plain");
        m.setContent("hello");
        return m;
    }

    private FileInfo file(Long id, Long ownerId, boolean deleted, boolean folder, String suffix) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setUserId(ownerId);
        f.setIsDeleted(deleted);
        f.setIsFolder(folder);
        f.setFilename("doc");
        f.setFileSuffix(suffix);
        return f;
    }

    private void lockAcquired() throws InterruptedException {
        when(redisClient.getLock(MqConstants.INDEX_LOCK_PREFIX + FILE_ID)).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
    }

    // ------------------------------------------------------------------ 消息本身

    @Test
    @DisplayName("TC-AS-004 消息为 null → 抛 IllegalArgumentException（触发重试）")
    void handleInsert_消息为空时抛异常() {
        Throwable thrown = catchThrowable(() -> consumer.handleInsert(null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引消息缺少文件ID");
        verifyNoInteractions(redisClient);
    }

    @Test
    @DisplayName("TC-AS-004 消息缺少文件 ID → 抛 IllegalArgumentException")
    void handleInsert_缺少文件ID时抛异常() {
        Throwable thrown = catchThrowable(() -> consumer.handleInsert(message(null, USER_ID)));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redisClient);
    }

    // ------------------------------------------------------------------ 分布式锁

    @Test
    @DisplayName("TC-AS-005 10 秒内拿不到锁 → 抛 IllegalStateException「索引繁忙」")
    void handleInsert_拿不到锁时抛异常() throws InterruptedException {
        when(redisClient.getLock(MqConstants.INDEX_LOCK_PREFIX + FILE_ID)).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);

        Throwable thrown = catchThrowable(() -> consumer.handleInsert(message(FILE_ID, USER_ID)));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("索引繁忙");
        verify(files, never()).getById(anyLong());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("TC-AS-007 等锁被中断 → 恢复中断标志并抛「索引任务已中断」")
    void handleInsert_等锁被中断时恢复中断标志() throws InterruptedException {
        when(redisClient.getLock(MqConstants.INDEX_LOCK_PREFIX + FILE_ID)).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));

        Throwable thrown = catchThrowable(() -> consumer.handleInsert(message(FILE_ID, USER_ID)));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("索引任务已中断");
        // 中断标志必须被恢复，否则线程池里的中断状态会丢
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();   // 清掉标志，避免影响同一线程里的后续用例
    }

    @Test
    @DisplayName("处理完成后释放锁")
    void handleInsert_完成后释放锁() throws InterruptedException {
        lockAcquired();
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, false, false, "txt"));

        consumer.handleInsert(message(FILE_ID, USER_ID));

        verify(lock).unlock();
    }

    // ------------------------------------------------------------------ 以 DB 为准

    @Test
    @DisplayName("TC-AS-003 文件已删除 → 删索引，不写文档")
    void handleInsert_文件已删除时删索引() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, true, false, "txt"));

        consumer.handleInsert(message(FILE_ID, USER_ID));

        verify(fileSearchService).del(FILE_ID);
        verify(fileSearchService, never()).save(any(FileDocument.class));
    }

    @Test
    @DisplayName("TC-AS-003 DB 里没有该文件 → 删索引")
    void handleInsert_文件不存在时删索引() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(null);

        consumer.handleInsert(message(FILE_ID, USER_ID));

        verify(fileSearchService).del(FILE_ID);
        verify(fileSearchService, never()).save(any(FileDocument.class));
    }

    @Test
    @DisplayName("TC-AS-002 DELETE 消息但文件仍有效 → 不动索引")
    void handleDelete_文件仍有效时跳过() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, false, false, "txt"));

        consumer.handleDelete(message(FILE_ID, USER_ID));

        verify(fileSearchService, never()).del(anyLong());
        verify(fileSearchService, never()).save(any(FileDocument.class));
    }

    @Test
    @DisplayName("TC-AS-006 消息归属与 DB 不一致 → 跳过，不写索引")
    void handleInsert_归属不一致时跳过() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, 99L, false, false, "txt"));

        consumer.handleInsert(message(FILE_ID, USER_ID));

        verify(fileSearchService, never()).save(any(FileDocument.class));
    }

    // ------------------------------------------------------------------ 正常同步

    @Test
    @DisplayName("TC-AS-001 普通文件 → 索引文件名带后缀")
    void handleInsert_普通文件名带后缀() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, false, false, "txt"));

        consumer.handleInsert(message(FILE_ID, USER_ID));

        ArgumentCaptor<FileDocument> captor = ArgumentCaptor.forClass(FileDocument.class);
        verify(fileSearchService).save(captor.capture());
        FileDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo(FILE_ID);
        assertThat(doc.getUserId()).isEqualTo(USER_ID);
        assertThat(doc.getFileName()).isEqualTo("doc.txt");
        assertThat(doc.getFileType()).isEqualTo("text/plain");
        assertThat(doc.getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("TC-AS-001 文件夹 → 索引文件名不带后缀")
    void handleInsert_文件夹不带后缀() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, false, true, null));

        consumer.handleInsert(message(FILE_ID, USER_ID));

        ArgumentCaptor<FileDocument> captor = ArgumentCaptor.forClass(FileDocument.class);
        verify(fileSearchService).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("doc");
    }

    @Test
    @DisplayName("TC-AS-001 UPDATE 消息与 INSERT 走同一套同步逻辑")
    void handleUpdate_按同步逻辑处理() throws InterruptedException {
        lockAcquired();
        when(files.getById(FILE_ID)).thenReturn(file(FILE_ID, USER_ID, false, false, "md"));

        consumer.handleUpdate(message(FILE_ID, USER_ID));

        verify(fileSearchService).save(any(FileDocument.class));
    }
}
