package com.ff.mycloud.service;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.ChunkUploadMapper;
import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.entity.ChunkTask;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.ChunkVO;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.service.impl.ChunkUploadServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ChunkUploadServiceImpl 单元测试
 * <p>测试分片上传初始化流程，覆盖正常/异常场景</p>
 */
class ChunkUploadServiceImplTest {

    @Mock private UserCenterMapperService userCenterMapperService;
    @Mock private StorageServiceFactory storageServiceFactory;
    @Mock private StorageService storageService;
    @Mock private ChunkUploadRedis chunkUploadRedis;
    @Mock private ChunkUploadMapper chunkUploadMapper;

    private ChunkUploadServiceImpl service;
    private MockedStatic<UserContext> userContextMock;

    // 测试常量
    private static final Long USER_ID = 1L;
    private static final String CUSTOM_UPLOAD_ID = "custom-uuid-12345678901234567890123456";
    private static final String OSS_UPLOAD_ID = "oss-upload-id-9876543210";
    private static final String TEST_OBJECT_KEY = "user_1/file123.txt";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ChunkUploadServiceImpl(
                userCenterMapperService, storageServiceFactory,
                chunkUploadRedis, chunkUploadMapper);

        // mock UserContext 静态方法
        userContextMock = mockStatic(UserContext.class);
        userContextMock.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
        User mockUser = new User();
        mockUser.setId(USER_ID);
        userContextMock.when(UserContext::getCurrentUser).thenReturn(mockUser);

        // 默认：存储服务就绪
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ==================== 正常流程 ====================

    @Test
    @DisplayName("initUpload 完整正常流程：空间预占→OSS初始化→Redis写入→返回ChunkVO")
    void shouldCompleteFullInitFlowWhenAllConditionsMet() {
        ChunkFile input = buildValidChunkFile();

        // 空间预占成功
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        // OSS 初始化成功
        when(storageService.initiateMultipartUpload(anyString())).thenReturn(OSS_UPLOAD_ID);
        // MapStruct 映射
        ChunkTask mappedTask = new ChunkTask();
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(mappedTask);
        // Redis 保存（void方法，无需mock返回值）

        Result<ChunkVO> result = service.initUpload(input);

        // 验证返回结果
        assertEquals(Integer.valueOf(CommonConstants.StatusCode.SUCCESS), result.getCode(), "应返回成功");
        assertNotNull(result.getData(), "ChunkVO 不应为 null");
        assertNotNull(result.getData().getUploadId(), "自定义uploadId不应为null");
        assertNotNull(result.getData().getOssUploadId(), "OSS uploadId不应为null");
        assertEquals(OSS_UPLOAD_ID, result.getData().getOssUploadId());

        // 验证关键调用
        verify(userCenterMapperService).reserveSpace(USER_ID, input.getFileSize());
        verify(storageService).initiateMultipartUpload(anyString());
        verify(chunkUploadMapper).toChunkTask(eq(input), anyString());
        verify(chunkUploadRedis).saveTask(any(ChunkTask.class));
    }

    @Test
    @DisplayName("initUpload 返回的uploadId和ossUploadId不应相同")
    void shouldReturnDistinctUploadIds() {
        ChunkFile input = buildValidChunkFile();

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString())).thenReturn(OSS_UPLOAD_ID);

        ChunkTask mappedTask = new ChunkTask();
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(mappedTask);

        Result<ChunkVO> result = service.initUpload(input);

        assertEquals(Integer.valueOf(CommonConstants.StatusCode.SUCCESS), result.getCode());
        ChunkVO vo = result.getData();
        assertNotNull(vo.getUploadId(), "自定义uploadId不应为null");
        assertNotNull(vo.getOssUploadId(), "OSS uploadId不应为null");
        // uploadId 是自定义生成的，ossUploadId 是OSS返回的，两者应不同
        assertNotEquals(vo.getUploadId(), vo.getOssUploadId(),
                "自定义uploadId和OSS uploadId应不同");
    }

    @Test
    @DisplayName("initUpload objectKey 应包含文件后缀")
    void shouldPreserveFileExtensionInObjectKey() {
        ChunkFile input = buildValidChunkFile();
        input.setFileName("test-document.pdf");

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString())).thenReturn(OSS_UPLOAD_ID);

        ChunkTask mappedTask = new ChunkTask();
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(mappedTask);

        Result<ChunkVO> result = service.initUpload(input);

        assertEquals(Integer.valueOf(CommonConstants.StatusCode.SUCCESS), result.getCode());
        // 验证 storageService 收到的 objectKey 以 .pdf 结尾
        verify(storageService).initiateMultipartUpload(
                argThat(key -> key != null && key.endsWith(".pdf")));
    }

    @Test
    @DisplayName("initUpload ChunkTask 应填充必备字段")
    void shouldSetAllRequiredFieldsOnChunkTask() {
        ChunkFile input = buildValidChunkFile();

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString())).thenReturn(OSS_UPLOAD_ID);

        ChunkTask mappedTask = new ChunkTask();
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(mappedTask);

        service.initUpload(input);

        // 验证 saveTask 收到的 ChunkTask 已完整填充
        verify(chunkUploadRedis).saveTask(argThat(task ->
                task != null
                && task.getOssUploadId() != null
                && task.getObjectKey() != null
                && task.getUserId() != null
                && task.getCompletedParts() != null
                && task.getPartEtags() != null
                && task.getSpacePreAllocated() != null
                && task.getSpacePreAllocated()
                && task.getCreatedAt() != null
                && task.getCreatedAt() > 0
        ));
    }

    // ==================== 异常 / 边界场景 ====================

    @Test
    @DisplayName("initUpload 空间不足应返回 error")
    void shouldReturnErrorWhenSpaceInsufficient() {
        ChunkFile input = buildValidChunkFile();

        // 空间预占失败
        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(false);

        Result<ChunkVO> result = service.initUpload(input);

        assertNotEquals(Integer.valueOf(CommonConstants.StatusCode.SUCCESS), result.getCode(), "空间不足应返回失败");
        assertNotNull(result.getMessage(), "错误消息不应为null");
        assertTrue(result.getMessage().contains("空间") || result.getMessage().contains("不足"),
                "错误消息应包含'空间不足'相关提示");

        // 空间不足时不应调用 OSS
        verify(storageServiceFactory, never()).getStorageService();
        verify(storageService, never()).initiateMultipartUpload(anyString());
        verify(chunkUploadRedis, never()).saveTask(any());
    }

    @Test
    @DisplayName("initUpload 无后缀文件名应正常处理")
    void shouldHandleFileNameWithoutExtension() {
        ChunkFile input = buildValidChunkFile();
        input.setFileName("noextensionfile");

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString())).thenReturn(OSS_UPLOAD_ID);

        ChunkTask mappedTask = new ChunkTask();
        when(chunkUploadMapper.toChunkTask(eq(input), anyString())).thenReturn(mappedTask);

        Result<ChunkVO> result = service.initUpload(input);

        assertEquals(Integer.valueOf(CommonConstants.StatusCode.SUCCESS), result.getCode(), "无后缀文件名也应成功");
        // 验证 objectKey 不以点号结尾（前端空后缀的 edge case）
        verify(storageService).initiateMultipartUpload(
                argThat(key -> key != null && !key.endsWith(".")));
    }

    @Test
    @DisplayName("initUpload OSS 初始化失败应向上抛异常")
    void shouldPropagateExceptionWhenOssInitFails() {
        ChunkFile input = buildValidChunkFile();

        when(userCenterMapperService.reserveSpace(USER_ID, input.getFileSize())).thenReturn(true);
        when(storageService.initiateMultipartUpload(anyString()))
                .thenThrow(new RuntimeException("OSS连接超时"));

        assertThrows(RuntimeException.class, () -> service.initUpload(input),
                "OSS异常应向上传播");

        // Redis 不应写入
        verify(chunkUploadRedis, never()).saveTask(any());
    }

    // ==================== 辅助方法 ====================

    private ChunkFile buildValidChunkFile() {
        ChunkFile f = new ChunkFile();
        f.setFileName("test-file.txt");
        f.setFileSize(1024 * 1024 * 600L); // 600MB（大文件）
        f.setFileType("text/plain");
        f.setParentId(0L);
        f.setTotalParts(120);
        f.setChunkSize(5 * 1024 * 1024); // 5MB per chunk
        f.setSha256Hash("abc123def456");
        return f;
    }
}
