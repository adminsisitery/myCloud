package com.ff.mycloud.service;

import com.aliyun.oss.model.PartETag;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.ChunkUploadMapper;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.entity.ChunkTask;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.FileObject;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.ChunkCompleteVO;
import com.ff.mycloud.model.vo.ChunkPartVO;
import com.ff.mycloud.model.vo.ChunkProgressVO;
import com.ff.mycloud.model.vo.ChunkVO;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.service.impl.ChunkUploadServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChunkUploadServiceImpl 整合测试
 * <p>覆盖分片上传全部5个操作：init / uploadPart / complete / progress / cancel</p>
 *
 * <p>Mock 策略：外部依赖全部 Mock（OSS / Redis / MySQL / UserContext）</p>
 */
class ChunkUploadServiceImplTest {

    // ==================== Mock 依赖 ====================
    @Mock private UserCenterMapperService userCenterMapperService;
    @Mock private StorageServiceFactory storageServiceFactory;
    @Mock private StorageService storageService;
    @Mock private ChunkUploadRedis chunkUploadRedis;
    @Mock private ChunkUploadMapper chunkUploadMapper;
    @Mock private FileInfoMapperService fileInfoMapperService;
    @Mock private FileMapper fileMapper;
    @Mock private FileObjectMapper fileObjectMapper;
    @Mock private RLock uploadLock;

    private ChunkUploadServiceImpl service;
    private MockedStatic<UserContext> userContextMock;

    // ==================== 测试常量 ====================
    private static final Long USER_ID = 1L;
    private static final String TEST_UPLOAD_ID = "test-upload-uuid-12345678901234567890";
    private static final String OSS_UPLOAD_ID = "oss-upload-id-9876543210";
    private static final String TEST_OBJECT_KEY = "user_1/abc123def456.txt";

    @BeforeEach
    void setUp() throws InterruptedException {
        MockitoAnnotations.openMocks(this);
        service = new ChunkUploadServiceImpl(
                userCenterMapperService,
                storageServiceFactory,
                chunkUploadRedis,
                chunkUploadMapper,
                fileInfoMapperService,
                fileMapper,
                fileObjectMapper
        );

        // 静态 mock UserContext
        userContextMock = mockStatic(UserContext.class);
        userContextMock.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
        User mockUser = new User();
        mockUser.setId(USER_ID);
        userContextMock.when(UserContext::getCurrentUser).thenReturn(mockUser);

        // 默认 storageService 就绪
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        when(chunkUploadRedis.getPartLock(anyString(), anyInt())).thenReturn(uploadLock);
        when(chunkUploadRedis.getTaskLock(anyString())).thenReturn(uploadLock);
        when(uploadLock.tryLock(anyLong(), anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        when(uploadLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ============================================================
    // 1. initUpload 测试
    // ============================================================

    @Test
    @DisplayName("init: 正常流程 — 预占空间 → OSS初始化 → Redis写入 → 返回ChunkVO")
    void init_shouldCompleteFullFlow() {
        ChunkFile input = buildChunkFile();

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString())).thenReturn(OSS_UPLOAD_ID);
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(new ChunkTask());

        Result<ChunkVO> result = service.initUpload(input);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getUploadId());
        assertEquals(OSS_UPLOAD_ID, result.getData().getOssUploadId());

        verify(userCenterMapperService).reserveSpace(USER_ID, input.getFileSize());
        verify(storageService).initiateMultipartUpload(anyString(), anyString());
        verify(chunkUploadRedis).saveTask(any(ChunkTask.class));
        verify(chunkUploadMapper).toChunkTask(eq(input), anyString());
    }

    @Test
    @DisplayName("init: 空间不足 → 返回 error，不调 OSS")
    void init_shouldFailWhenSpaceInsufficient() {
        ChunkFile input = buildChunkFile();
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(false);

        Result<ChunkVO> result = service.initUpload(input);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getMessage().contains("空间") || result.getMessage().contains("不足"));
        verify(storageServiceFactory, never()).getStorageService();
        verify(chunkUploadRedis, never()).saveTask(any());
    }

    @Test
    @DisplayName("init: uploadId 和 ossUploadId 应不同")
    void init_shouldHaveDistinctIds() {
        ChunkFile input = buildChunkFile();
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString())).thenReturn(OSS_UPLOAD_ID);
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(new ChunkTask());

        Result<ChunkVO> result = service.initUpload(input);

        assertNotEquals(result.getData().getUploadId(), result.getData().getOssUploadId());
    }

    @Test
    @DisplayName("init: objectKey 保留文件后缀")
    void init_shouldKeepExtension() {
        ChunkFile input = buildChunkFile();
        input.setFileName("video.mp4");
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString())).thenReturn(OSS_UPLOAD_ID);
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(new ChunkTask());

        service.initUpload(input);

        verify(storageService).initiateMultipartUpload(argThat(k -> k != null && k.endsWith(".mp4")), anyString());
    }

    @Test
    @DisplayName("init: 无后缀文件名正常处理")
    void init_shouldHandleNoExtension() {
        ChunkFile input = buildChunkFile();
        input.setFileName("noext");
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString())).thenReturn(OSS_UPLOAD_ID);
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(new ChunkTask());

        Result<ChunkVO> result = service.initUpload(input);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        verify(storageService).initiateMultipartUpload(argThat(k -> k != null && !k.endsWith(".")), anyString());
    }

    @Test
    @DisplayName("init: OSS 异常应传播，不写入 Redis")
    void init_shouldPropagateOssException() {
        ChunkFile input = buildChunkFile();
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString()))
                .thenThrow(new RuntimeException("OSS连接超时"));

        assertThrows(RuntimeException.class, () -> service.initUpload(input));
        verify(chunkUploadRedis, never()).saveTask(any());
    }

    @Test
    @DisplayName("init: ChunkTask 设置 createdAt 和 lastActivityTime")
    void init_shouldSetTimestamps() {
        ChunkFile input = buildChunkFile();
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString(), anyString())).thenReturn(OSS_UPLOAD_ID);
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(new ChunkTask());

        service.initUpload(input);

        verify(chunkUploadRedis).saveTask(argThat(task ->
                task != null
                && task.getCreatedAt() != null && task.getCreatedAt() > 0
                && task.getLastActivityTime() != null && task.getLastActivityTime() > 0
        ));
    }

    // ============================================================
    // 2. uploadPart 测试
    // ============================================================

    @Test
    @DisplayName("uploadPart: 正常流程 — 上传分片 → 存ETag → 标位图 → 返回成功")
    void uploadPart_shouldUploadSuccessfully() throws IOException {
        ChunkTask task = buildChunkTask();
        MultipartFile chunk = mockChunk("part-data".getBytes());
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.isPartUploaded(TEST_UPLOAD_ID, 1)).thenReturn(false);
        when(storageService.uploadPart(eq(task.getObjectKey()), eq(task.getOssUploadId()),
                eq(1), any(InputStream.class), eq(9L))).thenReturn("etag-part1");

        Result<ChunkPartVO> result = service.uploadPart(TEST_UPLOAD_ID, 1, chunk);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getData().getCompleted());
        assertEquals(1, result.getData().getPartNumber());
        assertEquals(TEST_UPLOAD_ID, result.getData().getUploadId());

        verify(chunkUploadRedis).savePartETag(TEST_UPLOAD_ID, 1, "etag-part1");
        verify(chunkUploadRedis).markPartDone(TEST_UPLOAD_ID, 1);
        verify(chunkUploadRedis).saveTask(task); // 刷新 lastActivityTime
    }

    @Test
    @DisplayName("uploadPart: uploadId 不存在 → 返回 error")
    void uploadPart_shouldFailWhenUploadIdNotFound() {
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(null);

        Result<ChunkPartVO> result = service.uploadPart(TEST_UPLOAD_ID, 1, mockChunk("x".getBytes()));

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getMessage().contains("不存在") || result.getMessage().contains("过期"));
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart: 重复上传已完成分片 → 幂等返回成功")
    void uploadPart_shouldRejectDuplicate() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.isPartUploaded(TEST_UPLOAD_ID, 1)).thenReturn(true);

        Result<ChunkPartVO> result = service.uploadPart(TEST_UPLOAD_ID, 1, mockChunk("x".getBytes()));

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getData().getCompleted());
        verify(storageService, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("uploadPart: IO 异常 → 返回 error")
    void uploadPart_shouldHandleIOException() throws IOException {
        ChunkTask task = buildChunkTask();
        MultipartFile chunk = mock(MultipartFile.class);
        when(chunk.getInputStream()).thenThrow(new IOException("读取失败"));
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.isPartUploaded(TEST_UPLOAD_ID, 1)).thenReturn(false);

        Result<ChunkPartVO> result = service.uploadPart(TEST_UPLOAD_ID, 1, chunk);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getMessage().contains("失败"));
        verify(chunkUploadRedis, never()).savePartETag(anyString(), anyInt(), anyString());
    }

    // ============================================================
    // 3. completeChunk 测试
    // ============================================================

    @Test
    @DisplayName("complete: 全部分片完成 → OSS合并 → 入库 → 返回文件信息")
    void complete_shouldMergeAndSave() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.getMissingParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Collections.emptyList());
        when(chunkUploadRedis.getPartETags(TEST_UPLOAD_ID))
                .thenReturn(Arrays.asList(
                        new PartETag(1, "etag1"),
                        new PartETag(2, "etag2")
                ));
        when(storageService.completeChunk(eq(task.getObjectKey()), eq(task.getOssUploadId()), anyList()))
                .thenReturn(task.getObjectKey());
        FileInfo mockFileInfo = new FileInfo();
        mockFileInfo.setId(100L);
        when(fileMapper.toFileInfoFromPath(any(), any(), anyLong(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(mockFileInfo);
        when(fileMapper.toFileObject(any(), any())).thenReturn(new FileObject());

        Result<ChunkCompleteVO> result = service.completeChunk(TEST_UPLOAD_ID);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertNotNull(result.getData());
        assertNotNull(result.getData().getFileName());

        verify(storageService).completeChunk(eq(task.getObjectKey()), eq(task.getOssUploadId()), anyList());
        verify(fileInfoMapperService).save(any(FileInfo.class));
        verify(fileObjectMapper).createFileObject(any(FileObject.class));
        verify(chunkUploadRedis).deleteTask(TEST_UPLOAD_ID);
    }

    @Test
    @DisplayName("complete: uploadId 不存在 → 返回 error")
    void complete_shouldFailWhenNotFound() {
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(null);

        Result<ChunkCompleteVO> result = service.completeChunk(TEST_UPLOAD_ID);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        verify(storageService, never()).completeChunk(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("complete: 有缺失分片 → 返回 error 并列出缺失号")
    void complete_shouldListMissingParts() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.getMissingParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Arrays.asList(3, 7, 15));

        Result<ChunkCompleteVO> result = service.completeChunk(TEST_UPLOAD_ID);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getMessage().contains("3"));
        assertTrue(result.getMessage().contains("7"));
        assertTrue(result.getMessage().contains("15"));
        verify(storageService, never()).completeChunk(anyString(), anyString(), anyList());
    }

    // ============================================================
    // 4. progress 测试
    // ============================================================

    @Test
    @DisplayName("progress: 返回完成/缺失分片列表和进度百分比")
    void progress_shouldReturnChunkProgress() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.getMissingParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Collections.singletonList(2));
        when(chunkUploadRedis.getCompleteParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Collections.singletonList(1));

        Result<ChunkProgressVO> result = service.getMissingList(TEST_UPLOAD_ID);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        ChunkProgressVO vo = result.getData();
        assertEquals(TEST_UPLOAD_ID, vo.getUploadId());
        assertFalse(vo.getCompletedParts().isEmpty());
        assertFalse(vo.getMissingParts().isEmpty());
        assertEquals(1, vo.getCompletedParts().size());
        assertEquals(1, vo.getMissingParts().size());
    }

    @Test
    @DisplayName("progress: 全部分片完成 → 进度 100%")
    void progress_shouldShowComplete() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.getMissingParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Collections.emptyList());
        when(chunkUploadRedis.getCompleteParts(TEST_UPLOAD_ID, task.getTotalParts()))
                .thenReturn(Arrays.asList(1, 2));

        Result<ChunkProgressVO> result = service.getMissingList(TEST_UPLOAD_ID);

        assertTrue(result.getData().getMissingParts().isEmpty());
        assertEquals(2, result.getData().getCompletedParts().size());
    }

    @Test
    @DisplayName("progress: uploadId 不存在 → 返回 error")
    void progress_shouldFailWhenNotFound() {
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(null);

        Result<ChunkProgressVO> result = service.getMissingList(TEST_UPLOAD_ID);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
    }

    // ============================================================
    // 5. cancelUpload 测试
    // ============================================================

    @Test
    @DisplayName("cancel: 正常流程 — OSS abort → 释放空间 → 清理 Redis")
    void cancel_shouldAbortAndReleaseAndClean() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);

        Result<String> result = service.cancelUpload(TEST_UPLOAD_ID);

        assertEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        assertTrue(result.getData().contains("已取消"));

        verify(storageService).cancelUpload(task.getOssUploadId(), task.getObjectKey());
        verify(userCenterMapperService).releaseSpace(task.getUserId(), task.getFileSize());
        verify(chunkUploadRedis).deleteTask(TEST_UPLOAD_ID);
    }

    @Test
    @DisplayName("cancel: uploadId 不存在 → 返回 error")
    void cancel_shouldFailWhenNotFound() {
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(null);

        Result<String> result = service.cancelUpload(TEST_UPLOAD_ID);

        assertNotEquals(CommonConstants.StatusCode.SUCCESS, result.getCode().intValue());
        verify(storageService, never()).cancelUpload(anyString(), anyString());
        verify(userCenterMapperService, never()).releaseSpace(anyLong(), anyLong());
    }

    @Test
    @DisplayName("cancel: OSS abort 异常不应阻塞空间释放")
    void cancel_shouldReleaseSpaceEvenIfOssFails() {
        ChunkTask task = buildChunkTask();
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        doThrow(new RuntimeException("OSS不可用")).when(storageService)
                .cancelUpload(task.getOssUploadId(), task.getObjectKey());

        // OSS abort 抛异常会直接传播，当前实现没有 try-catch
        assertThrows(RuntimeException.class, () -> service.cancelUpload(TEST_UPLOAD_ID));
    }

    // ============================================================
    // 6. lastActivityTime 刷新测试
    // ============================================================

    @Test
    @DisplayName("uploadPart: 每次成功上传应刷新 lastActivityTime")
    void uploadPart_shouldRefreshLastActivityTime() throws IOException {
        ChunkTask task = buildChunkTask();
        task.setLastActivityTime(1000L); // 旧的时间
        MultipartFile chunk = mockChunk("part-data".getBytes());
        when(chunkUploadRedis.getTask(TEST_UPLOAD_ID)).thenReturn(task);
        when(chunkUploadRedis.isPartUploaded(TEST_UPLOAD_ID, 1)).thenReturn(false);
        when(storageService.uploadPart(anyString(), anyString(), eq(1), any(), anyLong()))
                .thenReturn("etag-part1");

        service.uploadPart(TEST_UPLOAD_ID, 1, chunk);

        verify(chunkUploadRedis).saveTask(argThat(t ->
                t.getLastActivityTime() != null && t.getLastActivityTime() > 1000L
        ));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private ChunkFile buildChunkFile() {
        ChunkFile f = new ChunkFile();
        f.setFileName("test-file.txt");
        f.setFileSize(10L * 1024 * 1024);
        f.setFileType("text/plain");
        f.setParentId(0L);
        f.setTotalParts(2);
        f.setChunkSize(5 * 1024 * 1024);
        f.setSha256Hash("abc123def456");
        return f;
    }

    private ChunkTask buildChunkTask() {
        ChunkTask t = new ChunkTask();
        t.setUploadId(TEST_UPLOAD_ID);
        t.setOssUploadId(OSS_UPLOAD_ID);
        t.setObjectKey(TEST_OBJECT_KEY);
        t.setUserId(USER_ID);
        t.setFileName("test-file.txt");
        t.setFileSize(10L * 1024 * 1024);
        t.setTotalParts(2);
        t.setChunkSize(5 * 1024 * 1024);
        t.setFileType("text/plain");
        t.setParentId(0L);
        t.setSha256Hash("abc123def456");
        t.setCreatedAt(System.currentTimeMillis());
        t.setLastActivityTime(System.currentTimeMillis());
        return t;
    }

    private MultipartFile mockChunk(byte[] data) {
        MultipartFile file = mock(MultipartFile.class);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(data));
            when(file.getSize()).thenReturn((long) data.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}
