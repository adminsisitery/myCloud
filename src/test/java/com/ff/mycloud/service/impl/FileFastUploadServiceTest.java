package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.dto.FastUploadRequest;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.FastUploadVO;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 秒传链路单元测试
 * <p>
 * 覆盖两段：{@code checkFilesHash}（只判定）与 {@code fastUpload}（落库）。
 * 重点验证三件事：判定不落库、落库不信任入参、重复调用幂等。
 * </p>
 */
@DisplayName("秒传 check + fast-upload")
class FileFastUploadServiceTest {

    @Mock private StorageServiceFactory storageServiceFactory;
    @Mock private UserCenterMapperService userCenterMapperService;
    @Mock private FileMapper fileMapper;
    @Mock private FileInfoMapperService fileInfoMapperService;
    @Mock private UserMapperServiceImpl userMapperServiceImpl;
    @Mock private UpLoadProgressService upLoadProgressService;
    @Mock private RabbitMqService rabbitMqService;
    @Mock private FileObjectMapper fileObjectMapper;
    @Mock private FileRecentAccess fileRecentAccess;
    @Mock private UserContext userContext;
    @Mock private TransactionTemplate transactionTemplate;

    private FileServiceImpl service;
    private MockedStatic<UserContext> userContextMock;

    private static final Long USER_ID = 1L;
    private static final String HASH = "a".repeat(64);
    private static final long SIZE = 4096L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FileServiceImpl(
                storageServiceFactory, userCenterMapperService,
                fileMapper, fileInfoMapperService,
                userContext, userMapperServiceImpl,
                upLoadProgressService, rabbitMqService,
                fileObjectMapper, (HttpCodeStatusMapper) null, fileRecentAccess, transactionTemplate
        );
        userContextMock = mockStatic(UserContext.class);
        userContextMock.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

        User user = new User();
        user.setId(USER_ID);
        when(userMapperServiceImpl.findOneByUserId(USER_ID)).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ==================== 辅助方法 ====================

    /** 库里已存在的物理对象（跑过一次真实上传的文件记录） */
    private FileInfo existingObject(Long id, Long size) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setUserId(USER_ID);
        f.setFilename("报告");
        f.setFileSuffix("pdf");
        f.setFileType("application/pdf");
        f.setFileSize(size);
        f.setSha256Hash(HASH);
        f.setPath("user_1/object-abc");
        f.setIsFolder(false);
        f.setIsDeleted(false);
        return f;
    }

    private FileCheck check(String hash, Long size) {
        FileCheck c = new FileCheck();
        c.setSha256Hash(hash);
        c.setFileSize(size);
        c.setFileName("报告.pdf");
        return c;
    }

    private FastUploadRequest request(String fileName, Long fileSize, String hash, Long parentId) {
        FastUploadRequest r = new FastUploadRequest();
        r.setFileName(fileName);
        r.setFileSize(fileSize);
        r.setSha256Hash(hash);
        r.setParentId(parentId);
        return r;
    }

    // ==================== checkFilesHash ====================

    @Nested
    @DisplayName("checkFilesHash —— 只判定，不落库")
    class Check {

        @Test
        @DisplayName("hash 或 size 缺失 → exists=false，且不查库")
        void missingParams() {
            assertFalse(service.checkFilesHash(check(null, SIZE)).getData().getExists());
            assertFalse(service.checkFilesHash(check("", SIZE)).getData().getExists());
            assertFalse(service.checkFilesHash(check(HASH, null)).getData().getExists());

            verifyNoInteractions(fileInfoMapperService);
        }

        @Test
        @DisplayName("同 hash 但 size 不同 → exists=false（防 hash 碰撞/伪造）")
        void sizeMismatch() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE + 1)));

            Result<FileCheckVO> result = service.checkFilesHash(check(HASH, SIZE));

            assertTrue(result.getData().getExists() == false);
            verify(fileInfoMapperService, never()).save(any());
        }

        @Test
        @DisplayName("hash + size 都命中 → exists=true，且不写库")
        void hitButNoSideEffect() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));

            Result<FileCheckVO> result = service.checkFilesHash(check(HASH, SIZE));

            assertTrue(result.getData().getExists());
            verify(fileInfoMapperService, never()).save(any());
            verifyNoInteractions(fileMapper);
        }
    }

    // ==================== fastUpload ====================

    @Nested
    @DisplayName("fastUpload —— 落库")
    class FastUpload {

        @Test
        @DisplayName("sha256 非法 → 400，不查库")
        void invalidHash() {
            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, "abc", 0L));

            assertEquals(ErrorCode.INVALID_PARAM.getHttpStatus(), result.getCode());
            verifyNoInteractions(fileInfoMapperService);
        }

        @Test
        @DisplayName("sha256 大写/带空格 → 归一化后仍可用")
        void hashNormalized() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));
            when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(List.of());
            when(fileMapper.toFileInfoForFastUpload(anyString(), anyString(), any(), any(), any(), anyString()))
                    .thenAnswer(inv -> existingObject(500L, SIZE));

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, " " + HASH.toUpperCase() + " ", 0L));

            assertEquals(200, result.getCode().intValue());
            // 归一化后的 hash 才用于查库，否则会漏掉已有记录
            verify(fileInfoMapperService).findByHashAndUserId(HASH, USER_ID);
        }

        @Test
        @DisplayName("fileName 空 / fileSize<=0 → 400")
        void invalidParams() {
            assertEquals(ErrorCode.INVALID_PARAM.getHttpStatus(),
                    service.fastUpload(request("  ", SIZE, HASH, 0L)).getCode());
            assertEquals(ErrorCode.INVALID_PARAM.getHttpStatus(),
                    service.fastUpload(request("报告.pdf", null, HASH, 0L)).getCode());
            assertEquals(ErrorCode.INVALID_PARAM.getHttpStatus(),
                    service.fastUpload(request("报告.pdf", 0L, HASH, 0L)).getCode());
            verifyNoInteractions(fileInfoMapperService);
        }

        @Test
        @DisplayName("库里没有匹配 size 的物理对象 → FAST_UPLOAD_UNAVAILABLE")
        void noPhysicalObject() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE + 1)));

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 0L));

            assertEquals(ErrorCode.FAST_UPLOAD_UNAVAILABLE.getHttpStatus(), result.getCode());
            assertEquals(ErrorCode.FAST_UPLOAD_UNAVAILABLE.getMessage(), result.getMessage());
            verify(fileInfoMapperService, never()).save(any());
        }

        @Test
        @DisplayName("目标目录不属于当前用户 → 404，不落库")
        void parentNotOwned() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));
            when(fileInfoMapperService.findByIdAndUserId(77L, USER_ID)).thenReturn(null);

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 77L));

            assertEquals(ErrorCode.FILE_NOT_FOUND.getHttpStatus(), result.getCode());
            verify(fileInfoMapperService, never()).save(any());
        }

        @Test
        @DisplayName("目标 parentId 指向文件而非文件夹 → 404")
        void parentIsFile() {
            FileInfo notFolder = existingObject(88L, SIZE);
            notFolder.setIsFolder(false);
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));
            when(fileInfoMapperService.findByIdAndUserId(88L, USER_ID)).thenReturn(notFolder);

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 88L));

            assertEquals(ErrorCode.FILE_NOT_FOUND.getHttpStatus(), result.getCode());
            verify(fileInfoMapperService, never()).save(any());
        }

        @Test
        @DisplayName("目标目录已有同 hash + 同名记录 → 幂等复用，不重复插入")
        void idempotent() {
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));
            when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L))
                    .thenReturn(List.of(existingObject(101L, SIZE)));

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 0L));

            assertEquals(200, result.getCode().intValue());
            assertEquals(101L, result.getData().getFileId());
            assertTrue(result.getData().getReused());
            verify(fileInfoMapperService, never()).save(any());
            verify(fileMapper, never()).toFileInfoForFastUpload(anyString(), anyString(), any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("目标目录有同 hash 但不同名 → 允许建第二条逻辑记录")
        void sameHashDifferentName() {
            FileInfo other = existingObject(101L, SIZE);
            other.setFilename("另一个名字");
            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                    .thenReturn(List.of(existingObject(100L, SIZE)));
            when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(List.of(other));
            when(fileMapper.toFileInfoForFastUpload(anyString(), anyString(), any(), any(), any(), anyString()))
                    .thenAnswer(inv -> existingObject(500L, SIZE));

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 0L));

            assertEquals(200, result.getCode().intValue());
            assertFalse(result.getData().getReused());
            verify(fileInfoMapperService).save(any());
        }

        @Test
        @DisplayName("正常落库 → 复用物理对象、补齐 fileSuffix、返回 fileId")
        void happyPath() {
            FileInfo existing = existingObject(100L, SIZE);
            FileInfo created = new FileInfo();
            created.setId(500L);
            created.setFilename("报告");
            created.setFileSize(SIZE);
            created.setPath(existing.getPath());

            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID)).thenReturn(List.of(existing));
            when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(List.of());
            when(fileMapper.toFileInfoForFastUpload(eq("报告.pdf"), eq("报告"), eq(existing),
                    eq(USER_ID), eq(0L), eq(HASH))).thenReturn(created);

            Result<FastUploadVO> result = service.fastUpload(request("报告.pdf", SIZE, HASH, 0L));

            assertEquals(200, result.getCode().intValue());
            assertEquals(500L, result.getData().getFileId());
            assertEquals("报告.pdf", result.getData().getFilename());
            assertFalse(result.getData().getReused());

            // 回归：MapStruct 没映射 fileSuffix，必须在 handleFastUpload 里补上，
            // 否则 listStarredFiles / ES 同步会拼出 "报告.null"
            assertEquals("pdf", created.getFileSuffix());
            assertNotNull(created.getCategoryId());

            verify(fileInfoMapperService).save(created);
            verify(rabbitMqService).sendInsertMessage(any());
        }

        @Test
        @DisplayName("正常落库：不碰存储层（秒传的定义就是零字节传输）")
        void neverTouchesStorage() {
            FileInfo existing = existingObject(100L, SIZE);
            FileInfo created = new FileInfo();
            created.setId(501L);
            created.setFilename("报告");
            created.setFileSize(SIZE);

            when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID)).thenReturn(List.of(existing));
            when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(List.of());
            when(fileMapper.toFileInfoForFastUpload(anyString(), anyString(), any(), any(), any(), anyString()))
                    .thenReturn(created);

            service.fastUpload(request("报告.pdf", SIZE, HASH, 0L));

            verifyNoInteractions(storageServiceFactory);
        }
    }
}
