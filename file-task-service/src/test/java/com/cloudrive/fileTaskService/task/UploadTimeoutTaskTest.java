package com.cloudrive.fileTaskService.task;

import com.cloudrive.fileTaskService.client.UserSpaceClient;
import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.model.entity.ChunkTask;
import com.cloudrive.fileTaskService.model.entity.UploadTask;
import com.cloudrive.fileTaskService.redis.ChunkUploadRedis;
import com.cloudrive.fileTaskService.redis.UploadProgressRedis;
import com.cloudrive.fileTaskService.service.StorageService;
import com.cloudrive.pan_common.enums.UploadTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UploadTimeoutTask 单元测试：上传超时清理。
 * <p>
 * 这个任务干的是「替卡住的上传善后」，出错代价很直接：
 * </p>
 * <ul>
 *   <li><b>多退</b>：任务其实已经落库成功，还去归还空间 → 用户白得配额，是资损；</li>
 *   <li><b>不退</b>：归还失败还写了终态 → 空间永远占着，用户查不出原因；</li>
 *   <li><b>重复退</b>：多个实例/多轮扫描同时处理同一任务。</li>
 * </ul>
 * <p>
 * 所以重点不是覆盖率，是幂等闸（spaceReleased / spacePreAllocated）和失败回滚这两件事。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UploadTimeoutTaskTest {

    private static final Long USER_ID = 7L;
    private static final String TASK_ID = "task-1";
    private static final String UPLOAD_ID = "chunk-1";
    private static final long SIZE = 2048L;

    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock UploadProgressRedis uploadProgressRedis;
    @Mock ChunkUploadRedis chunkUploadRedis;
    @Mock UserSpaceClient userSpaceClient;
    @Mock StorageService storageService;

    @InjectMocks UploadTimeoutTask task;

    private void lockAcquired(String lockKey) {
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    // ================================================================== 幂等闸的判定逻辑（纯逻辑，直接测）

    @Test
    @DisplayName("已落库成功（SUCCESS）的预占绝不能退 → holdsUnsettledReservation=false，防资损")
    void holdsUnsettledReservation_成功任务不再归还() {
        UploadTask ut = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        ut.setStatus(UploadTaskStatus.SUCCESS);
        ut.setSpaceReserved(true);   // 即便标记没清掉，也不该退

        assertThat(ut.holdsUnsettledReservation()).isFalse();
    }

    @Test
    @DisplayName("已归还过的任务不再处理（幂等）")
    void holdsUnsettledReservation_已归还不重复() {
        UploadTask ut = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        ut.setSpaceReserved(true);
        ut.setSpaceReleased(true);

        assertThat(ut.holdsUnsettledReservation()).isFalse();
    }

    @Test
    @DisplayName("显式预占且未结清 → 需要归还")
    void holdsUnsettledReservation_显式预占需要处理() {
        UploadTask ut = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        ut.setSpaceReserved(true);

        assertThat(ut.holdsUnsettledReservation()).isTrue();
    }

    @Test
    @DisplayName("历史任务（没有 spaceReserved 标记）按状态兜底：PENDING/UPLOADING 要处理，FAILED 不处理")
    void holdsUnsettledReservation_历史任务按状态兜底() {
        UploadTask pending = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        pending.setStatus(UploadTaskStatus.PENDING);
        assertThat(pending.holdsUnsettledReservation()).isTrue();

        UploadTask failed = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        failed.setStatus(UploadTaskStatus.FAILED);
        assertThat(failed.holdsUnsettledReservation()).isFalse();
    }

    // ================================================================== 普通上传：扫描

    @Test
    @DisplayName("拿不到分布式锁 → 跳过本轮，不扫 Redis")
    void scanTimeoutTasks_未抢到锁时跳过() {
        when(redissonClient.getLock(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        task.scanTimeoutTasks();

        verifyNoInteractions(uploadProgressRedis);
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("未超过 30 分钟 → 不动（不能把正在传的任务当超时清理掉）")
    void scanTimeoutTasks_未超时不处理() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of(TASK_ID));

        UploadTask fresh = mock(UploadTask.class);
        lenient().when(fresh.holdsUnsettledReservation()).thenReturn(true);
        lenient().when(fresh.getCreatedAt()).thenReturn(System.currentTimeMillis());   // 刚创建
        when(uploadProgressRedis.getTask(TASK_ID)).thenReturn(fresh);

        task.scanTimeoutTasks();

        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("缺少 userId → 跳过（不知道该还给谁，宁可不动）")
    void scanTimeoutTasks_缺userId时跳过() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of(TASK_ID));
        UploadTask stale = staleTask(null);
        when(uploadProgressRedis.getTask(TASK_ID)).thenReturn(stale);

        task.scanTimeoutTasks();

        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
        verify(uploadProgressRedis, never()).updateTask(anyString(), any());
    }

    @Test
    @DisplayName("幂等闸已占位（别的实例处理过）→ 不再归还")
    void scanTimeoutTasks_幂等闸拦住时不归还() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of(TASK_ID));
        UploadTask stale = staleTask(USER_ID);
        when(uploadProgressRedis.getTask(TASK_ID)).thenReturn(stale);
        when(uploadProgressRedis.updateTask(eq(TASK_ID), any())).thenReturn(false);

        task.scanTimeoutTasks();

        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("超时任务 → 归还空间并写 FAILED 终态")
    void scanTimeoutTasks_超时任务归还空间并置失败() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of(TASK_ID));
        UploadTask stale = staleTask(USER_ID);
        when(uploadProgressRedis.getTask(TASK_ID)).thenReturn(stale);
        when(uploadProgressRedis.updateTask(eq(TASK_ID), any())).thenReturn(true);

        task.scanTimeoutTasks();

        verify(userSpaceClient).releaseSpace(USER_ID, SIZE);

        // 第二次 updateTask 是写终态，抓出来在真实对象上跑一遍，确认真的落成 FAILED
        ArgumentCaptor<Function> captor = ArgumentCaptor.forClass(Function.class);
        verify(uploadProgressRedis, times(2)).updateTask(eq(TASK_ID), captor.capture());

        UploadTask real = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        real.setStatus(UploadTaskStatus.UPLOADING);
        captor.getAllValues().get(1).apply(real);

        assertThat(real.getStatus()).isEqualTo(UploadTaskStatus.FAILED);
        assertThat(real.isCompleted()).isTrue();
        assertThat(real.getMessage()).contains("上传超时");
    }

    @Test
    @DisplayName("归还空间失败 → 回滚「已归还」标记，等下一轮补偿，且不写终态")
    void scanTimeoutTasks_归还失败时回滚标记() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(List.of(TASK_ID));
        UploadTask stale = staleTask(USER_ID);
        when(uploadProgressRedis.getTask(TASK_ID)).thenReturn(stale);
        when(uploadProgressRedis.updateTask(eq(TASK_ID), any())).thenReturn(true);
        doThrow(new RuntimeException("user-service down")).when(userSpaceClient).releaseSpace(USER_ID, SIZE);

        task.scanTimeoutTasks();

        // 第一次占位、第二次回滚，所以恰好两次
        ArgumentCaptor<Function> captor = ArgumentCaptor.forClass(Function.class);
        verify(uploadProgressRedis, times(2)).updateTask(eq(TASK_ID), captor.capture());

        UploadTask real = new UploadTask(TASK_ID, "a.txt", SIZE, USER_ID);
        real.setSpaceReleased(true);              // 模拟第一次占位后的状态
        captor.getAllValues().get(1).apply(real); // 第二次应该是回滚

        assertThat(real.isSpaceReleased()).isFalse();
    }

    /** 造一个「创建于 1 小时前、还持有预占」的任务 mock。 */
    private UploadTask staleTask(Long userId) {
        UploadTask ut = mock(UploadTask.class);
        lenient().when(ut.holdsUnsettledReservation()).thenReturn(true);
        lenient().when(ut.getCreatedAt()).thenReturn(System.currentTimeMillis() - 60 * 60 * 1000L);
        lenient().when(ut.getUserId()).thenReturn(userId);
        lenient().when(ut.getTotalSize()).thenReturn(SIZE);
        lenient().when(ut.getStatus()).thenReturn(UploadTaskStatus.UPLOADING);
        return ut;
    }

    // ================================================================== 分片上传：扫描

    private ChunkTask chunkTask(Long userId, Long fileSize, Long lastActivity, Boolean storageCompleted) {
        ChunkTask t = new ChunkTask();
        t.setUploadId(UPLOAD_ID);
        t.setOssUploadId("oss-1");
        t.setObjectKey("user_7/big.bin");
        t.setUserId(userId);
        t.setFileSize(fileSize);
        t.setStorageCompleted(storageCompleted);
        t.setSpacePreAllocated(true);
        t.setCreatedAt(System.currentTimeMillis() - 48 * 60 * 60 * 1000L);
        t.setLastActivityTime(lastActivity);
        return t;
    }

    @Test
    @DisplayName("分片任务 24 小时内还有活动 → 不动")
    void scanChunkTimeoutTasks_未超时不处理() {
        lockAcquired(TaskConstants.LOCK_CHUNK_TIMEOUT_SCAN);
        when(chunkUploadRedis.scanAllTaskIds()).thenReturn(List.of(UPLOAD_ID));
        when(chunkUploadRedis.getTask(UPLOAD_ID))
                .thenReturn(chunkTask(USER_ID, SIZE, System.currentTimeMillis(), false));

        task.scanChunkTimeoutTasks();

        verifyNoInteractions(storageService);
        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("lastActivityTime 为空 → 回退用 createdAt 判定超时")
    void scanChunkTimeoutTasks_无活动时间时用创建时间兜底() {
        lockAcquired(TaskConstants.LOCK_CHUNK_TIMEOUT_SCAN);
        when(chunkUploadRedis.scanAllTaskIds()).thenReturn(List.of(UPLOAD_ID));
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(chunkTask(USER_ID, SIZE, null, false));

        task.scanChunkTimeoutTasks();

        verify(storageService).cancelUpload("oss-1", "user_7/big.bin");
        verify(userSpaceClient).releaseSpace(USER_ID, SIZE);
        verify(chunkUploadRedis).deleteTask(UPLOAD_ID);
    }

    @Test
    @DisplayName("已合并的分片任务 → 删对象；未合并 → 中止分片上传")
    void scanChunkTimeoutTasks_按是否已合并选择清理方式() {
        lockAcquired(TaskConstants.LOCK_CHUNK_TIMEOUT_SCAN);
        when(chunkUploadRedis.scanAllTaskIds()).thenReturn(List.of(UPLOAD_ID));
        when(chunkUploadRedis.getTask(UPLOAD_ID))
                .thenReturn(chunkTask(USER_ID, SIZE, 1L, true));   // storageCompleted = true

        task.scanChunkTimeoutTasks();

        verify(storageService).deleteFile("user_7/big.bin");
        verify(storageService, never()).cancelUpload(anyString(), anyString());
    }

    @Test
    @DisplayName("分片任务归还失败 → 回滚 spacePreAllocated，任务留着下一轮再试")
    void scanChunkTimeoutTasks_归还失败时回滚标记() {
        lockAcquired(TaskConstants.LOCK_CHUNK_TIMEOUT_SCAN);
        when(chunkUploadRedis.scanAllTaskIds()).thenReturn(List.of(UPLOAD_ID));
        ChunkTask chunk = chunkTask(USER_ID, SIZE, 1L, false);
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(chunk);
        doThrow(new RuntimeException("user-service down")).when(userSpaceClient).releaseSpace(USER_ID, SIZE);

        task.scanChunkTimeoutTasks();

        // 先置 false 保存，失败后再置回 true 保存
        assertThat(chunk.getSpacePreAllocated()).isTrue();
        verify(chunkUploadRedis, times(2)).saveTask(chunk);
        verify(chunkUploadRedis, never()).deleteTask(anyString());
    }

    @Test
    @DisplayName("分片任务缺 userId/fileSize → 跳过，不清理也不归还")
    void scanChunkTimeoutTasks_缺关键字段时跳过() {
        lockAcquired(TaskConstants.LOCK_CHUNK_TIMEOUT_SCAN);
        when(chunkUploadRedis.scanAllTaskIds()).thenReturn(List.of(UPLOAD_ID));
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(chunkTask(null, null, 1L, false));

        task.scanChunkTimeoutTasks();

        verifyNoInteractions(storageService);
        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("扫描列表为空 → 什么都不做")
    void scanTimeoutTasks_无任务时什么都不做() {
        lockAcquired(TaskConstants.LOCK_UPLOAD_TIMEOUT_SCAN);
        when(uploadProgressRedis.scanAllTaskIds()).thenReturn(Collections.emptyList());

        task.scanTimeoutTasks();

        verifyNoInteractions(userSpaceClient);
    }
}
