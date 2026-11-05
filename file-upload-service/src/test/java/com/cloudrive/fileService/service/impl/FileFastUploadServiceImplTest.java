package com.cloudrive.fileService.service.impl;

import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.dto.FastUploadRequest;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.entity.FileCheck;
import com.cloudrive.fileService.model.vo.FastUploadVO;
import com.cloudrive.fileService.model.vo.FileCheckVO;
import com.cloudrive.fileService.mq.MqProducer;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.UpLoadProgressService;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileFastUploadServiceImpl 单元测试：秒传判定与秒传执行。
 * <p>
 * 对应 TEST-CASES.md 的「FileFastUploadServiceImpl.checkFilesHash / fastUpload」测试点。
 * 秒传从链路上看是「不传字节建一条记录」，风险全在校验：hash 格式、物理对象是否真存在、
 * 目标目录归属、以及重复点击时的幂等。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileFastUploadServiceImplTest {

    private static final Long USER_ID = 7L;
    /** 合法 SHA-256（64 位十六进制） */
    private static final String HASH = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private static final long SIZE = 12L;

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock FileMapper fileMapper;
    @Mock UpLoadProgressService upLoadProgressService;
    @Mock MqProducer mqProducer;

    @InjectMocks FileFastUploadServiceImpl service;

    // ------------------------------------------------------------------ 夹具

    private FileInfo file(Long id, Long size, String name, String suffix, String hash, boolean folder) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setUserId(USER_ID);
        f.setFileSize(size);
        f.setFilename(name);
        f.setFileSuffix(suffix);
        f.setSha256Hash(hash);
        f.setIsFolder(folder);
        f.setIsDeleted(false);
        return f;
    }

    private FastUploadRequest request(String hash, String fileName, Long fileSize, Long parentId) {
        FastUploadRequest r = new FastUploadRequest();
        r.setSha256Hash(hash);
        r.setFileName(fileName);
        r.setFileSize(fileSize);
        r.setParentId(parentId);
        return r;
    }

    // ================================================================== checkFilesHash

    @Test
    @DisplayName("check：hash 或 size 缺失 → exists=false，不查库")
    void check_参数不完整时直接判为不可秒传() {
        assertThat(service.checkFilesHash(check(null, SIZE)).getData().getExists()).isFalse();
        assertThat(service.checkFilesHash(check("   ", SIZE)).getData().getExists()).isFalse();
        assertThat(service.checkFilesHash(check(HASH, null)).getData().getExists()).isFalse();

        verify(fileInfoMapperService, never()).findByHashAndUserId(anyString(), anyLong());
    }

    @Test
    @DisplayName("check：同 hash 同 size 命中 → exists=true")
    void check_命中时返回可秒传() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, SIZE, "test", "txt", HASH, false)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            FileCheckVO vo = service.checkFilesHash(check(HASH, SIZE)).getData();

            assertThat(vo.getExists()).isTrue();
        }
    }

    @Test
    @DisplayName("check：hash 命中但 size 不同 → exists=false（防假秒传）")
    void check_size不一致时不算命中() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, 999L, "other", "txt", HASH, false)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.checkFilesHash(check(HASH, SIZE)).getData().getExists()).isFalse();
        }
    }

    private FileCheck check(String hash, Long size) {
        FileCheck c = new FileCheck();
        c.setSha256Hash(hash);
        c.setFileSize(size);
        return c;
    }

    // ================================================================== fastUpload 参数校验

    @Test
    @DisplayName("fastUpload：hash 非 64 位十六进制 → INVALID_PARAM(400)")
    void fastUpload_hash非法时拒绝() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<FastUploadVO> r = service.fastUpload(request("not-a-hash", "a.txt", SIZE, 0L));

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).contains("sha256Hash 必须是 64 位十六进制");
        }
        verify(fileInfoMapperService, never()).save(any(FileInfo.class));
    }

    @Test
    @DisplayName("fastUpload：fileName 为空 → INVALID_PARAM(400)")
    void fastUpload_文件名为空时拒绝() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<FastUploadVO> r = service.fastUpload(request(HASH, "   ", SIZE, 0L));

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("fileName 不能为空");
        }
    }

    @Test
    @DisplayName("fastUpload：fileSize <= 0 → INVALID_PARAM(400)")
    void fastUpload_文件大小非法时拒绝() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.fastUpload(request(HASH, "a.txt", 0L, 0L)).getCode()).isEqualTo(400);
            assertThat(service.fastUpload(request(HASH, "a.txt", null, 0L)).getMessage())
                    .isEqualTo("fileSize 必须大于 0");
        }
    }

    // ================================================================== fastUpload 业务校验

    @Test
    @DisplayName("fastUpload：云端没有可复用物理对象 → FAST_UPLOAD_UNAVAILABLE(400)")
    void fastUpload_物理对象不存在时拒绝() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID)).thenReturn(Collections.emptyList());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<FastUploadVO> r = service.fastUpload(request(HASH, "a.txt", SIZE, 0L));

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("该文件在云端不存在，无法秒传");
        }
        verify(fileInfoMapperService, never()).save(any(FileInfo.class));
    }

    @Test
    @DisplayName("fastUpload：目标目录不存在 → FILE_NOT_FOUND(404)")
    void fastUpload_目标目录不存在时拒绝() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, SIZE, "src", "txt", HASH, false)));
        when(fileInfoMapperService.findByIdAndUserId(88L, USER_ID)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<FastUploadVO> r = service.fastUpload(request(HASH, "a.txt", SIZE, 88L));

            assertThat(r.getCode()).isEqualTo(404);
            assertThat(r.getMessage()).isEqualTo("目标目录不存在");
        }
    }

    @Test
    @DisplayName("fastUpload：同 hash + 同名已存在 → 幂等复用，不重复落库")
    void fastUpload_幂等命中时复用已有记录() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, SIZE, "src", "txt", HASH, false)));
        // 目标目录里已有「同 hash + 同名 + 同后缀」的记录
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L))
                .thenReturn(List.of(file(66L, SIZE, "a", "txt", HASH, false)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            FastUploadVO vo = service.fastUpload(request(HASH, "a.txt", SIZE, 0L)).getData();

            assertThat(vo.getFileId()).isEqualTo(66L);
            assertThat(vo.getReused()).isTrue();
        }
        verify(fileInfoMapperService, never()).save(any(FileInfo.class));
    }

    @Test
    @DisplayName("fastUpload：成功落库，reused=false")
    void fastUpload_成功时新增文件记录() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, SIZE, "src", "txt", HASH, false)));
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(Collections.emptyList());
        when(fileMapper.toFileInfoForFastUpload(anyString(), anyString(), any(FileInfo.class),
                anyLong(), anyLong(), anyString()))
                .thenReturn(file(100L, SIZE, "a", "txt", HASH, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            FastUploadVO vo = service.fastUpload(request(HASH, "a.txt", SIZE, 0L)).getData();

            assertThat(vo.getReused()).isFalse();
            assertThat(vo.getFileId()).isEqualTo(100L);
            assertThat(vo.getFilename()).isEqualTo("a.txt");
        }
        verify(fileInfoMapperService).save(any(FileInfo.class));
    }

    @Test
    @DisplayName("fastUpload：同级已有同名文件 → 自动改名 a(1).txt")
    void fastUpload_同名冲突时自动加序号() {
        when(fileInfoMapperService.findByHashAndUserId(HASH, USER_ID))
                .thenReturn(List.of(file(1L, SIZE, "src", "txt", HASH, false)));
        // 同名但 hash 不同：不构成幂等，会走「重命名后再落库」
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L))
                .thenReturn(List.of(file(55L, SIZE, "a", "txt", "another-hash", false)));
        when(fileMapper.toFileInfoForFastUpload(anyString(), anyString(), any(FileInfo.class),
                anyLong(), anyLong(), anyString()))
                .thenReturn(file(101L, SIZE, "a(1)", "txt", HASH, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            service.fastUpload(request(HASH, "a.txt", SIZE, 0L));
        }

        // 传给 Mapper 的原始文件名应为去重后的 a(1).txt
        verify(fileMapper).toFileInfoForFastUpload(
                org.mockito.ArgumentMatchers.eq("a(1).txt"),
                org.mockito.ArgumentMatchers.eq("a(1)"),
                any(FileInfo.class), anyLong(), anyLong(), anyString());
    }
}
