package com.cloudrive.fileTaskService.task;

import com.cloudrive.fileTaskService.common.TaskConstants;
import com.cloudrive.fileTaskService.repositorys.FileInfoMapper;
import com.cloudrive.fileTaskService.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TrashCleanupTask 单元测试：回收站 30 天自动清理。
 * <p>
 * 对应 TEST-CASES.md 的 TC-TK-027 ~ TC-TK-032。
 * 这个模块没有任何 HTTP 入口，定时任务的行为只能靠单测兜住。重点在两件事：
 * 「什么时候不该删 OSS 对象」（文件夹、多行引用同一物理对象）和「单条失败不能拖垮整批」。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TrashCleanupTaskTest {

    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock FileInfoMapper fileInfoMapper;
    @Mock StorageService storageService;

    @InjectMocks TrashCleanupTask task;

    private void lockAcquired() {
        when(redissonClient.getLock(TaskConstants.LOCK_TRASH_CLEANUP)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    // ------------------------------------------------------------------ 加锁

    @Test
    @DisplayName("cleanupTrash：拿不到分布式锁 → 直接跳过本轮，不查库")
    void cleanupTrash_未抢到锁时跳过() {
        when(redissonClient.getLock(TaskConstants.LOCK_TRASH_CLEANUP)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        task.cleanupTrash();

        verifyNoInteractions(fileInfoMapper);
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("cleanupTrash：正常执行结束后释放锁")
    void cleanupTrash_执行完成后释放锁() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(Collections.emptyList());

        task.cleanupTrash();

        verify(lock).unlock();
    }

    @Test
    @DisplayName("cleanupTrash：扫描异常被吞掉并释放锁（不让定时任务线程挂掉）")
    void cleanupTrash_扫描异常时仍释放锁() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenThrow(new RuntimeException("db down"));

        task.cleanupTrash();

        verify(lock).unlock();
    }

    // ------------------------------------------------------------------ 30 天判定

    @Test
    @DisplayName("cleanupTrash：扫描截止时间 = 当前时间 - 30 天")
    void cleanupTrash_按30天窗口扫描() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(Collections.emptyList());

        task.cleanupTrash();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fileInfoMapper).findOverTimeFile(captor.capture());
        LocalDateTime cutoff = captor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusDays(TaskConstants.TRASH_RETENTION_DAYS);
        // 允许几秒误差（构造 cutoff 的时间点略早于断言时间）
        assertThat(cutoff).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }

    // ------------------------------------------------------------------ OSS 删除判定

    @Test
    @DisplayName("TC-TK-027/031 普通文件 + 唯一引用 → 删 OSS 对象 + 删 DB 记录")
    void cleanupTrash_唯一引用的普通文件删除物理对象() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(1L));
        when(fileInfoMapper.findIsFolderById(1L)).thenReturn(false);
        when(fileInfoMapper.findPathById(1L)).thenReturn("user_7/a.txt");
        when(fileInfoMapper.findSha256HashById(1L)).thenReturn("hash-1");
        when(fileInfoMapper.countByHash("hash-1")).thenReturn(1L);

        task.cleanupTrash();

        verify(storageService).deleteFile("user_7/a.txt");
        verify(fileInfoMapper).deleteById(1L);
    }

    @Test
    @DisplayName("TC-TK-030 文件夹 → 不删 OSS 对象，只删 DB 记录")
    void cleanupTrash_文件夹只删记录() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(2L));
        when(fileInfoMapper.findIsFolderById(2L)).thenReturn(true);

        task.cleanupTrash();

        verifyNoInteractions(storageService);
        verify(fileInfoMapper).deleteById(2L);
    }

    @Test
    @DisplayName("TC-TK-029 同一物理对象被多行引用（秒传/复制）→ 保留 OSS 对象，只删当前行")
    void cleanupTrash_多引用时保留物理对象() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(3L));
        when(fileInfoMapper.findIsFolderById(3L)).thenReturn(false);
        when(fileInfoMapper.findPathById(3L)).thenReturn("user_7/a.txt");
        when(fileInfoMapper.findSha256HashById(3L)).thenReturn("hash-shared");
        when(fileInfoMapper.countByHash("hash-shared")).thenReturn(2L);

        task.cleanupTrash();

        verifyNoInteractions(storageService);
        verify(fileInfoMapper).deleteById(3L);
    }

    @Test
    @DisplayName("TC-TK-031 hash 为空 → 按「无引用」处理，删 OSS 对象")
    void cleanupTrash_哈希为空时删除物理对象() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(4L));
        when(fileInfoMapper.findIsFolderById(4L)).thenReturn(false);
        when(fileInfoMapper.findPathById(4L)).thenReturn("user_7/legacy.bin");
        when(fileInfoMapper.findSha256HashById(4L)).thenReturn(null);

        task.cleanupTrash();

        verify(storageService).deleteFile("user_7/legacy.bin");
        verify(fileInfoMapper, never()).countByHash(any());
        verify(fileInfoMapper).deleteById(4L);
    }

    @Test
    @DisplayName("path 为空白 → 不碰 OSS，只删记录")
    void cleanupTrash_路径为空时跳过物理删除() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(5L));
        when(fileInfoMapper.findIsFolderById(5L)).thenReturn(false);
        when(fileInfoMapper.findPathById(5L)).thenReturn("   ");

        task.cleanupTrash();

        verifyNoInteractions(storageService);
        verify(fileInfoMapper).deleteById(5L);
    }

    // ------------------------------------------------------------------ 异常隔离

    @Test
    @DisplayName("TC-TK-032 单条删除异常 → 记日志后继续处理后续记录")
    void cleanupTrash_单条失败不中断整批() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(List.of(6L, 7L));
        when(fileInfoMapper.findIsFolderById(anyLong())).thenReturn(true);
        doThrow(new RuntimeException("record locked")).when(fileInfoMapper).deleteById(6L);

        task.cleanupTrash();

        // 第一条抛异常后，第二条仍然被处理
        verify(fileInfoMapper).deleteById(6L);
        verify(fileInfoMapper).deleteById(7L);
    }

    @Test
    @DisplayName("扫描结果为空 → 不调用任何删除")
    void cleanupTrash_无过期文件时什么都不删() {
        lockAcquired();
        when(fileInfoMapper.findOverTimeFile(any())).thenReturn(Collections.emptyList());

        task.cleanupTrash();

        verifyNoInteractions(storageService);
        verify(fileInfoMapper, times(0)).deleteById(anyLong());
    }
}
