package com.cloudrive.fileService.service.impl;

import com.aliyun.oss.model.ObjectMetadata;
import com.cloudrive.fileService.client.UserSpaceClient;
import com.cloudrive.fileService.config.properties.OssProProperties;
import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.entity.DirectUploadTask;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.vo.ChunkCompleteVO;
import com.cloudrive.fileService.mq.MqProducer;
import com.cloudrive.fileService.redis.DirectUploadRedis;
import com.cloudrive.fileService.repositorys.FileObjectMapper;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.StorageService;
import com.cloudrive.fileService.service.StorageServiceFactory;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DirectUploadServiceImpl 单元测试：STS 直传的确认与取消。
 * <p>
 * 对应「关键路径清单」里剩下的两条：<b>越权</b>（处理他人直传任务）和
 * <b>数据一致性</b>（OSS 上的对象大小 / 哈希与初始化参数不一致却落了库）。
 * </p>
 * <p>
 * 直传链路的风险点在于「客户端直接往 OSS 写字节」，服务端看不到过程，
 * 只能在 complete 这一步把大小和 x-oss-meta-sha256 对一遍——这两项对不上就必须拒绝，
 * 否则库里会留下一条指向脏对象的记录。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DirectUploadServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String TASK_ID = "direct-1";
    private static final long SIZE = 4096L;
    private static final String HASH = "hash-1";

    @Mock OssProProperties oss;
    @Mock DirectUploadRedis redis;
    @Mock UserSpaceClient userSpaceClient;
    @Mock FileInfoMapperService fileInfoService;
    @Mock StorageServiceFactory storageFactory;
    @Mock FileMapper fileMapper;
    @Mock FileObjectMapper fileObjectMapper;
    @Mock TransactionTemplate transactionTemplate;
    @Mock MqProducer mqProducer;
    @Mock StorageService storageService;
    @Mock RLock lock;

    @InjectMocks DirectUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(storageFactory.getStorageService()).thenReturn(storageService);
    }

    // ------------------------------------------------------------------ 夹具

    private DirectUploadTask task(Long ownerId, long expiresAt) {
        DirectUploadTask t = new DirectUploadTask();
        t.setTaskId(TASK_ID);
        t.setUserId(ownerId);
        t.setObjectKey("user_7/abc.bin");
        t.setFileName("a.bin");
        t.setFileSize(SIZE);
        t.setFileType("application/octet-stream");
        t.setParentId(0L);
        t.setSha256Hash(HASH);
        t.setCreatedAt(System.currentTimeMillis());
        t.setExpiresAt(expiresAt);
        return t;
    }

    private ObjectMetadata metadata(long size, String hash) {
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(size);
        Map<String, String> userMeta = new HashMap<>();
        if (hash != null) {
            userMeta.put("sha256", hash);
        }
        md.setUserMetadata(userMeta);
        return md;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    private void lockOkFor(long waitMinutes) throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, waitMinutes, TimeUnit.MINUTES)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    // ================================================================== complete

    @Test
    @DisplayName("complete：等不到任务锁 → 拒绝，防并发重复确认")
    void complete_并发确认时拒绝() throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(false);

        BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

        assertThat(ex.getMessage()).contains("直传任务正在确认");
    }

    @Test
    @DisplayName("complete：任务不存在或已完成 → FILE_NOT_FOUND(404)")
    void complete_任务不存在时拒绝() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(404);
            assertThat(ex.getMessage()).contains("直传任务不存在或已完成");
        }
    }

    @Test
    @DisplayName("complete：确认他人任务 → NO_PERMISSION(403)，不碰 OSS 也不落库")
    void complete_他人任务时拒绝() throws InterruptedException {
        lockOkFor(2);
        DirectUploadTask other = task(99L, System.currentTimeMillis() + 60_000);
        when(redis.get(TASK_ID)).thenReturn(other);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(403);
        }
        verify(storageService, never()).getObjectMetadata(any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("complete：任务已过期 → 拒绝（过期后 OSS 对象可能已被清理）")
    void complete_任务过期时拒绝() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() - 1_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getMessage()).contains("直传任务已过期");
        }
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("complete：OSS 对象大小与初始化参数不一致 → 拒绝落库")
    void complete_对象大小不一致时拒绝() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));
        when(storageService.getObjectMetadata("user_7/abc.bin")).thenReturn(metadata(SIZE - 1, HASH));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getMessage()).contains("大小与初始化参数不一致");
        }
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("complete：缺少 x-oss-meta-sha256 → 拒绝落库（防客户端传了别的文件）")
    void complete_哈希元数据缺失时拒绝() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));
        when(storageService.getObjectMetadata("user_7/abc.bin")).thenReturn(metadata(SIZE, null));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getMessage()).contains("x-oss-meta-sha256");
        }
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("complete：大小与哈希都对得上 → 落库、删任务、返回 fileId")
    void complete_校验通过时落库() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));
        when(storageService.getObjectMetadata("user_7/abc.bin")).thenReturn(metadata(SIZE, HASH));

        FileInfo saved = new FileInfo();
        saved.setId(500L);
        when(transactionTemplate.execute(any())).thenReturn(saved);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            ChunkCompleteVO vo = service.complete(TASK_ID);

            assertThat(vo.getFileId()).isEqualTo(500L);
            assertThat(vo.getFileSize()).isEqualTo(SIZE);
        }
        verify(redis).delete(TASK_ID);
    }

    @Test
    @DisplayName("complete：落库返回 null → 报「保存文件元数据失败」，不留半截任务")
    void complete_落库失败时报错() throws InterruptedException {
        lockOkFor(2);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));
        when(storageService.getObjectMetadata("user_7/abc.bin")).thenReturn(metadata(SIZE, HASH));
        when(transactionTemplate.execute(any())).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.complete(TASK_ID));

            assertThat(ex.getMessage()).contains("保存文件元数据失败");
        }
        verify(redis, never()).delete(TASK_ID);
    }

    // ================================================================== cancel

    @Test
    @DisplayName("cancel：取消他人任务 → NO_PERMISSION(403)，不删对象不还空间")
    void cancel_他人任务时拒绝() throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redis.get(TASK_ID)).thenReturn(task(99L, System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.cancel(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(403);
        }
        verify(storageService, never()).deleteFile(any());
        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("cancel：任务正在处理 → 拒绝")
    void cancel_任务处理中时拒绝() throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(false);

        BusinessException ex = bizExceptionOf(() -> service.cancel(TASK_ID));

        assertThat(ex.getMessage()).contains("直传任务正在处理");
    }

    @Test
    @DisplayName("cancel：正常取消 → 删临时对象 + 归还空间 + 删任务")
    void cancel_成功时三处都清理() throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cancel(TASK_ID);
        }

        verify(storageService).deleteFile("user_7/abc.bin");
        verify(userSpaceClient).releaseSpace(USER_ID, SIZE);
        verify(redis).delete(TASK_ID);
    }

    @Test
    @DisplayName("cancel：删临时对象失败 → finally 仍归还空间并删任务（不能让配额卡住）")
    void cancel_删对象失败仍归还空间() throws InterruptedException {
        when(redis.getLock(TASK_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redis.get(TASK_ID)).thenReturn(task(USER_ID, System.currentTimeMillis() + 60_000));
        doThrow(new RuntimeException("oss error")).when(storageService).deleteFile(any());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            catchThrowable(() -> service.cancel(TASK_ID));
        }

        verify(userSpaceClient).releaseSpace(USER_ID, SIZE);
        verify(redis).delete(TASK_ID);
    }
}
