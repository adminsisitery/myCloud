package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.client.UserSpaceClient;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.mq.MqProducer;
import com.cloudrive.fileManageService.repositorys.FileObjectMapper;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.StorageService;
import com.cloudrive.pan_common.exceotion.BusinessException;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RecycleBinServiceImpl 单元测试：软删除、彻底删除、恢复、清空。
 * <p>
 * 对应 TEST-CASES.md 的「RecycleBinServiceImpl」测试点。
 * 接口层能覆盖到一部分，但「他人文件」「子树一起删」「空间释放多少」这类判断在 Service 里，
 * 用单测锁分支比靠接口造数据便宜得多。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RecycleBinServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock FileObjectMapper fileObjectMapper;
    @Mock UserSpaceClient userSpaceClient;
    @Mock StorageService storageService;
    @Mock MqProducer mqProducer;

    @InjectMocks RecycleBinServiceImpl service;

    // ------------------------------------------------------------------ 夹具

    private FileInfo file(Long id, Long ownerId, boolean folder, Long size, boolean deleted) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setUserId(ownerId);
        f.setIsFolder(folder);
        f.setFileSize(size);
        f.setIsDeleted(deleted);
        f.setFilename("doc");
        f.setFileSuffix(folder ? null : "txt");
        f.setParentId(0L);
        f.setLocalFilePath("doc" + (folder ? "/" : ".txt"));
        f.setPath("user_7/doc.txt");
        f.setSha256Hash("hash-" + id);
        return f;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    // ================================================================== 软删除

    @Test
    @DisplayName("deleteSingleFile：fileId 为空 → INVALID_PARAM(400)")
    void deleteSingleFile_文件ID为空时拒绝() {
        BusinessException ex = bizExceptionOf(() -> service.deleteSingleFile(null));

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("文件ID不能为空");
    }

    @Test
    @DisplayName("deleteSingleFile：文件不存在 → FILE_NOT_FOUND(404)")
    void deleteSingleFile_文件不存在时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.deleteSingleFile(1L));

            assertThat(ex.getCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("deleteSingleFile：他人文件 → NO_PERMISSION(403)，且不落任何更新")
    void deleteSingleFile_他人文件时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, 99L, false, 1024L, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.deleteSingleFile(1L));

            assertThat(ex.getCode()).isEqualTo(403);
        }
        verify(fileInfoMapperService, never()).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("deleteSingleFile：普通文件 → 软删除并释放其占用空间")
    void deleteSingleFile_普通文件释放空间() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, 1024L, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.deleteSingleFile(1L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo("删除成功");
        }

        verify(userSpaceClient).releaseSpace(USER_ID, 1024L);
        verify(fileInfoMapperService).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("deleteSingleFile：文件夹 → 子树一起软删，空间按子树文件大小求和释放")
    void deleteSingleFile_文件夹连子树一起软删() {
        FileInfo folder = file(1L, USER_ID, true, null, false);
        folder.setLocalFilePath("docs/");
        FileInfo childA = file(2L, USER_ID, false, 100L, false);
        FileInfo childB = file(3L, USER_ID, false, 200L, false);

        when(fileInfoMapperService.getById(1L)).thenReturn(folder);
        when(fileInfoMapperService.getSubTree("docs/", USER_ID)).thenReturn(List.of(folder, childA, childB));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.deleteSingleFile(1L);
        }

        // 三行都更新（含根节点）
        verify(fileInfoMapperService, times(3)).updateById(any(FileInfo.class));
        // 文件夹本身不计入空间，只算两个文件
        verify(userSpaceClient).releaseSpace(USER_ID, 300L);
    }

    @Test
    @DisplayName("deleteSingleFile：MQ 投递失败不影响删除结果")
    void deleteSingleFile_旁路消息失败不阻断() {
        FileInfo plain = file(1L, USER_ID, false, 512L, false);
        when(fileInfoMapperService.getById(1L)).thenReturn(plain);
        doThrow(new RuntimeException("mq down")).when(mqProducer).sendIndexDelete(any());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.deleteSingleFile(1L);

            assertThat(result.getCode()).isEqualTo(200);
        }
        verify(userSpaceClient).releaseSpace(USER_ID, 512L);
    }

    // ================================================================== 批量删除

    @Test
    @DisplayName("batchDeleteFile：ids 为空 → INVALID_PARAM(400)")
    void batchDeleteFile_空ids时拒绝() {
        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.batchDeleteFile("  "));

            assertThat(ex.getCode()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("batchDeleteFile：查到的数量少于请求数量（含他人文件）→ NO_PERMISSION(403)")
    void batchDeleteFile_存在无权文件时整批拒绝() {
        when(fileInfoMapperService.findInIdAndUserId(List.of(1L, 2L), USER_ID))
                .thenReturn(List.of(file(1L, USER_ID, false, 10L, false)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.batchDeleteFile("1,2"));

            assertThat(ex.getCode()).isEqualTo(403);
        }
        verify(fileInfoMapperService, never()).updateById(any(FileInfo.class));
    }

    @Test
    @DisplayName("batchDeleteFile：全部属于本人 → 逐个软删并汇总释放空间")
    void batchDeleteFile_成功时逐个软删() {
        when(fileInfoMapperService.findInIdAndUserId(List.of(1L, 2L), USER_ID))
                .thenReturn(List.of(file(1L, USER_ID, false, 10L, false),
                        file(2L, USER_ID, false, 20L, false)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.batchDeleteFile("1,2");
        }

        verify(fileInfoMapperService, times(2)).updateById(any(FileInfo.class));
        verify(userSpaceClient).releaseSpace(USER_ID, 30L);
    }

    // ================================================================== 彻底删除

    @Test
    @DisplayName("tureDeleteFile：记录不存在 → 跳过，不报错")
    void tureDeleteFile_记录不存在时跳过() {
        when(fileInfoMapperService.getById(1L)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.tureDeleteFile("1");

            assertThat(result.getCode()).isEqualTo(200);
        }
        verify(fileInfoMapperService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("tureDeleteFile：他人文件 → NO_PERMISSION(403)")
    void tureDeleteFile_他人文件时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, 99L, false, 10L, true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.tureDeleteFile("1"));

            assertThat(ex.getCode()).isEqualTo(403);
        }
        verify(fileInfoMapperService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("tureDeleteFile：唯一引用 → 删物理对象 + 删记录")
    void tureDeleteFile_唯一引用时删物理对象() {
        FileInfo f = file(1L, USER_ID, false, 10L, true);
        when(fileInfoMapperService.getById(1L)).thenReturn(f);
        when(fileInfoMapperService.countFiles("hash-1")).thenReturn(1L);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.tureDeleteFile("1");
        }

        verify(storageService).deleteFile("user_7/doc.txt");
        verify(fileInfoMapperService).removeById(1L);
    }

    @Test
    @DisplayName("tureDeleteFile：秒传产生的多引用 → 保留物理对象，只删这一行")
    void tureDeleteFile_多引用时保留物理对象() {
        FileInfo f = file(1L, USER_ID, false, 10L, true);
        when(fileInfoMapperService.getById(1L)).thenReturn(f);
        when(fileInfoMapperService.countFiles("hash-1")).thenReturn(3L);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.tureDeleteFile("1");
        }

        verifyNoInteractions(storageService);
        verify(fileInfoMapperService).removeById(1L);
    }

    @Test
    @DisplayName("tureDeleteFile：文件夹 → 不碰 OSS 对象")
    void tureDeleteFile_文件夹不删物理对象() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, true, null, true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.tureDeleteFile("1");
        }

        verifyNoInteractions(storageService);
        verify(fileInfoMapperService).removeById(1L);
    }

    @Test
    @DisplayName("tureDeleteFile：OSS 删除抛异常 → 忽略异常，仍删数据库记录")
    void tureDeleteFile_OSS失败仍删记录() {
        FileInfo f = file(1L, USER_ID, false, 10L, true);
        when(fileInfoMapperService.getById(1L)).thenReturn(f);
        when(fileInfoMapperService.countFiles("hash-1")).thenReturn(1L);
        doThrow(new RuntimeException("oss error")).when(storageService).deleteFile(anyString());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.tureDeleteFile("1");
        }

        verify(fileInfoMapperService).removeById(1L);
    }

    // ================================================================== 恢复

    @Test
    @DisplayName("restoreFile：文件不存在 → FILE_NOT_FOUND(404)")
    void restoreFile_文件不存在时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(null);

        BusinessException ex = bizExceptionOf(() -> service.restoreFile(1L));

        assertThat(ex.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("restoreFile：他人文件 → NO_PERMISSION(403)")
    void restoreFile_他人文件时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, 99L, false, 10L, true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.restoreFile(1L));

            assertThat(ex.getCode()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("restoreFile：文件不在回收站 → FILE_NOT_IN_TRASH(400)")
    void restoreFile_不在回收站时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, 10L, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.restoreFile(1L));

            assertThat(ex.getCode()).isEqualTo(400);
            assertThat(ex.getMessage()).isEqualTo("该文件不在回收站中");
        }
    }

    @Test
    @DisplayName("restoreFile：原目录已被删 → 归位到根目录")
    void restoreFile_原目录已删时归位到根目录() {
        FileInfo target = file(1L, USER_ID, false, 10L, true);
        target.setParentId(5L);
        FileInfo deletedParent = file(5L, USER_ID, true, null, true);

        when(fileInfoMapperService.getById(1L)).thenReturn(target);
        when(fileInfoMapperService.getById(5L)).thenReturn(deletedParent);
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(Collections.emptyList());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.restoreFile(1L);
        }

        assertThat(target.getParentId()).isEqualTo(0L);
        assertThat(target.getIsDeleted()).isFalse();
    }

    @Test
    @DisplayName("restoreFile：成功 → 恢复空间占用（addUsedSpace）")
    void restoreFile_成功时恢复空间占用() {
        FileInfo target = file(1L, USER_ID, false, 2048L, true);
        when(fileInfoMapperService.getById(1L)).thenReturn(target);
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(Collections.emptyList());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.restoreFile(1L);

            assertThat(result.getData()).isEqualTo("恢复成功");
        }

        verify(userSpaceClient).addUsedSpace(USER_ID, 2048L);
        verify(fileInfoMapperService).updateById(target);
    }

    @Test
    @DisplayName("restoreFile：同级已有同名 → 自动改名为 doc(已恢复)")
    void restoreFile_同名冲突时自动改名() {
        FileInfo target = file(1L, USER_ID, false, 10L, true);
        FileInfo sibling = file(2L, USER_ID, false, 10L, false);

        when(fileInfoMapperService.getById(1L)).thenReturn(target);
        when(fileInfoMapperService.findByParentIdAndUserId(USER_ID, 0L)).thenReturn(List.of(sibling));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.restoreFile(1L);
        }

        assertThat(target.getFilename()).isEqualTo("doc(已恢复)");
    }

    // ================================================================== 清空回收站

    @Test
    @DisplayName("cleanTrash：回收站为空 → 不做任何删除")
    void cleanTrash_为空时什么都不做() {
        when(fileInfoMapperService.getTrashFile(USER_ID)).thenReturn(Collections.emptyList());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Result<String> result = service.cleanTrash();

            assertThat(result.getData()).isEqualTo("清空回收站成功");
        }
        verifyNoInteractions(storageService);
        verify(fileInfoMapperService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("cleanTrash：文件夹不删 OSS 对象，普通文件按引用数决定")
    void cleanTrash_按类型与引用数删除() {
        FileInfo folder = file(1L, USER_ID, true, null, true);
        FileInfo single = file(2L, USER_ID, false, 10L, true);   // 唯一引用
        FileInfo shared = file(3L, USER_ID, false, 10L, true);   // 多引用

        when(fileInfoMapperService.getTrashFile(USER_ID)).thenReturn(List.of(folder, single, shared));
        when(fileInfoMapperService.countFiles("hash-2")).thenReturn(1L);
        when(fileInfoMapperService.countFiles("hash-3")).thenReturn(2L);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cleanTrash();
        }

        verify(storageService, times(1)).deleteFile(anyString());
        verify(fileInfoMapperService, times(3)).removeById(anyLong());
    }

    @Test
    @DisplayName("cleanTrash：hash 为空的历史记录 → 不删 OSS")
    void cleanTrash_哈希为空时不删物理对象() {
        FileInfo legacy = file(1L, USER_ID, false, 10L, true);
        legacy.setSha256Hash(null);
        when(fileInfoMapperService.getTrashFile(USER_ID)).thenReturn(List.of(legacy));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cleanTrash();
        }

        verifyNoInteractions(storageService);
        verify(fileInfoMapperService).removeById(1L);
    }
}
