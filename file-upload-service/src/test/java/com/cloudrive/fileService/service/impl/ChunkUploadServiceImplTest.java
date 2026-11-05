package com.cloudrive.fileService.service.impl;

import com.aliyun.oss.model.PartETag;
import com.cloudrive.fileService.client.UserSpaceClient;
import com.cloudrive.fileService.mapper.ChunkUploadMapper;
import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.entity.ChunkFile;
import com.cloudrive.fileService.model.entity.ChunkTask;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.vo.ChunkCompleteVO;
import com.cloudrive.fileService.model.vo.ChunkPartVO;
import com.cloudrive.fileService.model.vo.ChunkVO;
import com.cloudrive.fileService.mq.MqProducer;
import com.cloudrive.fileService.redis.ChunkUploadRedis;
import com.cloudrive.fileService.repositorys.FileObjectMapper;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.StorageService;
import com.cloudrive.fileService.service.StorageServiceFactory;
import com.cloudrive.pan_common.result.Result;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ChunkUploadServiceImpl 单元测试。
 * <p>
 * 这里不追覆盖率，只锁三类会出事的点：
 * </p>
 * <ol>
 *   <li><b>越权</b>：分片上传 / 合并 / 取消都必须校验任务归属。取消尤其危险——
 *       放过去就等于让别人能释放你的空间配额。</li>
 *   <li><b>空间泄漏</b>：预占的空间在取消、初始化失败时必须归还，否则用户空间越用越少还查不出原因。</li>
 *   <li><b>数据完整性</b>：缺分片不能合并、重复上传要幂等、分片大小要和初始化参数对得上。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ChunkUploadServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String UPLOAD_ID = "up-123456";
    private static final long FILE_SIZE = 2 * 1024 * 1024L;

    @Mock UserSpaceClient userSpaceClient;
    @Mock StorageServiceFactory storageServiceFactory;
    @Mock ChunkUploadRedis chunkUploadRedis;
    @Mock ChunkUploadMapper chunkUploadMapper;
    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock FileMapper fileMapper;
    @Mock FileObjectMapper fileObjectMapper;
    @Mock MqProducer mqProducer;
    @Mock StorageService storageService;

    @InjectMocks ChunkUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        // 共享桩：拿存储实现不涉及分支，用 lenient 避免严格模式误报
        lenient().when(storageServiceFactory.getStorageService()).thenReturn(storageService);
    }

    // ------------------------------------------------------------------ 夹具

    private ChunkTask task(Long ownerId, boolean storageCompleted) {
        ChunkTask t = new ChunkTask();
        t.setUploadId(UPLOAD_ID);
        t.setOssUploadId("oss-upload-1");
        t.setObjectKey("user_7/abc.bin");
        t.setUserId(ownerId);
        t.setFileName("big.bin");
        t.setFileSize(FILE_SIZE);
        t.setFileType("application/octet-stream");
        t.setParentId(0L);
        t.setTotalParts(2);
        t.setChunkSize((int) (FILE_SIZE / 2));
        t.setSha256Hash("hash-1");
        t.setSpacePreAllocated(true);
        t.setStorageCompleted(storageCompleted);
        t.setCreatedAt(System.currentTimeMillis());
        t.setLastActivityTime(System.currentTimeMillis());
        return t;
    }

    private MultipartFile chunk(long size, boolean empty) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.isEmpty()).thenReturn(empty);
        lenient().when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{(byte) size}));
        return file;
    }

    // ================================================================== initUpload：空间

    @Test
    @DisplayName("init：空间不足 → 拒绝，且不创建 OSS 分片任务")
    void init_空间不足时拒绝() {
        ChunkFile request = new ChunkFile();
        request.setFileName("big.bin");
        request.setFileSize(FILE_SIZE);
        request.setTotalParts(2);
        request.setChunkSize((int) (FILE_SIZE / 2));
        request.setParentId(0L);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            when(userSpaceClient.reserveSpace(USER_ID, FILE_SIZE)).thenReturn(false);

            Result<ChunkVO> result = service.initUpload(request);

            assertThat(result.getCode()).isNotEqualTo(200);
            assertThat(result.getMessage()).contains("空间不足");
        }
        verify(storageService, never()).initiateMultipartUpload(anyString(), any());
    }

    @Test
    @DisplayName("init：OSS 初始化失败 → 释放刚预占的空间（否则配额永久泄漏）")
    void init_OSS失败时归还空间() {
        ChunkFile request = new ChunkFile();
        request.setFileName("big.bin");
        request.setFileSize(FILE_SIZE);
        request.setTotalParts(2);
        request.setChunkSize((int) (FILE_SIZE / 2));
        request.setParentId(0L);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            when(userSpaceClient.reserveSpace(USER_ID, FILE_SIZE)).thenReturn(true);
            when(storageService.initiateMultipartUpload(anyString(), any()))
                    .thenThrow(new RuntimeException("oss down"));

            try {
                service.initUpload(request);
            } catch (RuntimeException expected) {
                assertThat(expected.getMessage()).contains("oss down");
            }
        }

        // 预占成功了又初始化失败，必须还回去
        verify(userSpaceClient).releaseSpace(USER_ID, FILE_SIZE);
        verify(chunkUploadRedis, never()).saveTask(any(ChunkTask.class));
    }

    // ================================================================== uploadPart：越权与校验

    @Test
    @DisplayName("uploadPart：任务不存在 → 提示 uploadId 过期")
    void uploadPart_任务不存在() {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(null);

        Result<ChunkPartVO> result = service.uploadPart(UPLOAD_ID, 1, null);

        assertThat(result.getMessage()).contains("uploadId不存在或已过期");
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("uploadPart：往他人任务传分片 → 拒绝，不碰存储")
    void uploadPart_他人任务时拒绝() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(99L, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<ChunkPartVO> result = service.uploadPart(UPLOAD_ID, 1, chunk(1024, false));

            assertThat(result.getMessage()).contains("无权访问此上传任务");
        }
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart：分片序号越界 → 拒绝")
    void uploadPart_分片序号越界() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.uploadPart(UPLOAD_ID, 3, chunk(1024, false)).getMessage())
                    .contains("分片序号超出范围");
            assertThat(service.uploadPart(UPLOAD_ID, 0, chunk(1024, false)).getMessage())
                    .contains("分片序号超出范围");
        }
    }

    @Test
    @DisplayName("uploadPart：分片内容为空 → 拒绝")
    void uploadPart_分片为空() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.uploadPart(UPLOAD_ID, 1, chunk(0, true)).getMessage())
                    .contains("分片内容不能为空");
        }
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart：同分片重复上传 → 幂等返回成功，不再传一次 OSS")
    void uploadPart_重复上传幂等() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getPartLock(UPLOAD_ID, 1)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(chunkUploadRedis.isPartUploaded(UPLOAD_ID, 1)).thenReturn(true);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<ChunkPartVO> result = service.uploadPart(UPLOAD_ID, 1, chunk(1024, false));

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getCompleted()).isTrue();
        }
        // 关键：重复上传不能再花一次带宽
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart：拿不到分片锁 → 提示正在上传，避免并发写同一分片")
    void uploadPart_并发上传时拒绝() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getPartLock(UPLOAD_ID, 1)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(false);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.uploadPart(UPLOAD_ID, 1, chunk(1024, false)).getMessage())
                    .contains("分片正在上传");
        }
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart：分片大小超过初始化参数 → 拒绝（防止拼出错误大小的文件）")
    void uploadPart_分片大小不一致() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getPartLock(UPLOAD_ID, 1)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(chunkUploadRedis.isPartUploaded(UPLOAD_ID, 1)).thenReturn(false);

        long oversize = FILE_SIZE;   // 比初始化的 chunkSize（1MB）大
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.uploadPart(UPLOAD_ID, 1, chunk(oversize, false)).getMessage())
                    .contains("分片大小与初始化参数不一致");
        }
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    // ================================================================== completeChunk

    @Test
    @DisplayName("complete：缺分片 → 列出缺失分片号，不调用合并")
    void complete_缺失分片时拒绝() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(chunkUploadRedis.getMissingParts(UPLOAD_ID, 2)).thenReturn(List.of(2));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<ChunkCompleteVO> result = service.completeChunk(UPLOAD_ID);

            assertThat(result.getCode()).isNotEqualTo(200);
            assertThat(result.getMessage()).contains("缺失分片号").contains("2");
        }
        verify(storageService, never()).completeChunk(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("complete：合并他人任务 → 拒绝")
    void complete_他人任务时拒绝() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(99L, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.completeChunk(UPLOAD_ID).getMessage()).contains("无权访问此上传任务");
        }
        verify(storageService, never()).completeChunk(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("complete：等锁被中断 → 恢复中断标志并提示合并被中断")
    void complete_合并被中断() throws Exception {
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 2, TimeUnit.MINUTES)).thenThrow(new InterruptedException("interrupted"));

        Result<ChunkCompleteVO> result = service.completeChunk(UPLOAD_ID);

        assertThat(result.getMessage()).contains("合并请求被中断");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    // ================================================================== cancelUpload：越权与空间

    @Test
    @DisplayName("cancel：取消他人任务 → 拒绝，且绝不能归还别人的空间")
    void cancel_他人任务时拒绝且不释放空间() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(99L, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.cancelUpload(UPLOAD_ID).getMessage()).contains("无权访问此上传任务");
        }
        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
        verify(chunkUploadRedis, never()).deleteTask(anyString());
    }

    @Test
    @DisplayName("cancel：未合并的任务 → 中止 OSS 分片上传 + 归还空间 + 删任务")
    void cancel_未合并时中止并归还空间() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, false));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.cancelUpload(UPLOAD_ID);

            assertThat(result.getData()).isEqualTo("已取消");
        }

        verify(storageService).cancelUpload("oss-upload-1", "user_7/abc.bin");
        verify(userSpaceClient).releaseSpace(USER_ID, FILE_SIZE);
        verify(chunkUploadRedis).deleteTask(UPLOAD_ID);
    }

    @Test
    @DisplayName("cancel：已合并的任务 → 删已合并对象（而不是再中止分片），空间照还")
    void cancel_已合并时删除对象() throws Exception {
        when(chunkUploadRedis.getTask(UPLOAD_ID)).thenReturn(task(USER_ID, true));
        RLock lock = mock(RLock.class);
        when(chunkUploadRedis.getTaskLock(UPLOAD_ID)).thenReturn(lock);
        when(lock.tryLock(5, 1, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cancelUpload(UPLOAD_ID);
        }

        verify(storageService).deleteFile("user_7/abc.bin");
        verify(storageService, never()).cancelUpload(anyString(), anyString());
        verify(userSpaceClient).releaseSpace(USER_ID, FILE_SIZE);
    }
}
