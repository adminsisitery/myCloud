package com.cloudrive.fileDownloadService.service.impl;

import com.cloudrive.fileDownloadService.mq.FolderDownloadMqProducer;
import com.cloudrive.fileDownloadService.redis.FolderDownloadRedis;
import com.cloudrive.fileDownloadService.service.FileInfoMapperService;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.domain.folder.FolderDownloadStatus;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import com.cloudrive.fileDownloadService.model.entity.FileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FolderDownloadTaskServiceImpl 单元测试：文件夹异步打包任务。
 * <p>
 * 对应「关键路径清单」里剩下的两条：<b>越权</b>（查/取消他人任务）和
 * <b>过期仍可下载</b>（临时 ZIP 有明显生命周期，过期后必须拒掉，否则别人拿到链接可以长期拉数据）。
 * </p>
 * <p>
 * 另外两条阈值（2 万文件 / 200GB）也锁住了：它们挡住的是「一个请求把打包机打爆」，
 * 属于可用性问题，靠接口测试造数据不现实。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FolderDownloadTaskServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long FOLDER_ID = 100L;
    private static final String TASK_ID = "task-1";
    private static final long GB = 1024L * 1024 * 1024;

    @Mock FolderDownloadRedis redis;
    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock StorageService storageService;
    @Mock FolderDownloadMqProducer folderDownloadMqProducer;

    @InjectMocks FolderDownloadTaskServiceImpl service;

    // ------------------------------------------------------------------ 夹具

    private FileInfo folder() {
        FileInfo f = new FileInfo();
        f.setId(FOLDER_ID);
        f.setUserId(USER_ID);
        f.setFilename("docs");
        f.setIsFolder(true);
        f.setLocalFilePath("docs/");
        return f;
    }

    private FileInfo plainFile(long size) {
        FileInfo f = new FileInfo();
        f.setId(1L);
        f.setUserId(USER_ID);
        f.setFilename("a.bin");
        f.setIsFolder(false);
        f.setFileSize(size);
        return f;
    }

    private FolderDownloadTask task(Long ownerId, FolderDownloadStatus status, String objectKey, long expiresAt) {
        FolderDownloadTask t = new FolderDownloadTask();
        t.setTaskId(TASK_ID);
        t.setUserId(ownerId);
        t.setFolderId(FOLDER_ID);
        t.setFolderName("docs");
        t.setStatus(status);
        t.setObjectKey(objectKey);
        t.setExpiresAt(expiresAt);
        t.setMessage("等待后台打包");
        return t;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    // ================================================================== 归属校验

    @Test
    @DisplayName("查询任务：不存在 → FILE_NOT_FOUND「文件夹下载任务不存在」")
    void getOwnedTask_任务不存在时拒绝() {
        when(redis.get(TASK_ID)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.getOwnedTask(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getHttpStatus());
            assertThat(ex.getMessage()).contains("文件夹下载任务不存在");
        }
    }

    @Test
    @DisplayName("查询任务：他人任务 → NO_PERMISSION(403)")
    void getOwnedTask_他人任务时拒绝() {
        when(redis.get(TASK_ID)).thenReturn(
                task(99L, FolderDownloadStatus.SUCCESS, "zip/a.zip", System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.getOwnedTask(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.NO_PERMISSION.getHttpStatus());
        }
    }

    // ================================================================== 可下载判定

    @Test
    @DisplayName("下载判定：任务还没成功 → 压缩包尚未生成")
    void getDownloadableTask_未成功时拒绝() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.PACKAGING, "zip/a.zip", System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.getDownloadableTask(TASK_ID));

            assertThat(ex.getMessage()).contains("压缩包尚未生成");
        }
    }

    @Test
    @DisplayName("下载判定：状态成功但 objectKey 为空 → 同样按未生成处理")
    void getDownloadableTask_无对象时不放行() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.SUCCESS, null, System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(bizExceptionOf(() -> service.getDownloadableTask(TASK_ID)).getMessage())
                    .contains("压缩包尚未生成");
        }
    }

    @Test
    @DisplayName("下载判定：压缩包已过期 → 拒绝（临时 ZIP 不能长期可拉）")
    void getDownloadableTask_过期时拒绝() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.SUCCESS, "zip/a.zip", System.currentTimeMillis() - 1_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.getDownloadableTask(TASK_ID));

            assertThat(ex.getMessage()).contains("压缩包已过期");
        }
    }

    @Test
    @DisplayName("下载判定：成功且未过期 → 放行")
    void getDownloadableTask_成功且未过期时放行() {
        FolderDownloadTask ready =
                task(USER_ID, FolderDownloadStatus.SUCCESS, "zip/a.zip", System.currentTimeMillis() + 60_000);
        when(redis.get(TASK_ID)).thenReturn(ready);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.getDownloadableTask(TASK_ID)).isSameAs(ready);
        }
    }

    // ================================================================== 创建

    @Test
    @DisplayName("创建：目录不存在 → FILE_NOT_FOUND")
    void create_目录不存在时拒绝() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.create(FOLDER_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getHttpStatus());
        }
        verify(redis, never()).save(any());
    }

    @Test
    @DisplayName("创建：目标是普通文件 → CANNOT_DOWNLOAD_FOLDER「目标不是文件夹」")
    void create_目标不是文件夹时拒绝() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(plainFile(1024L));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.create(FOLDER_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.CANNOT_DOWNLOAD_FOLDER.getHttpStatus());
            assertThat(ex.getMessage()).contains("目标不是文件夹");
        }
    }

    @Test
    @DisplayName("创建：子树文件数超过 2 万 → FILE_TOO_LARGE（防止一个请求打爆打包机）")
    void create_文件数超限时拒绝() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(folder());
        List<FileInfo> huge = new ArrayList<>(20_001);
        for (int i = 0; i < 20_001; i++) {
            FileInfo f = plainFile(1L);
            f.setId((long) i);
            huge.add(f);
        }
        when(fileInfoMapperService.getSubTree("docs/", USER_ID)).thenReturn(huge);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.create(FOLDER_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getHttpStatus());
            assertThat(ex.getMessage()).contains("文件数量超过20000");
        }
        verify(redis, never()).save(any());
    }

    @Test
    @DisplayName("创建：子树总大小超过 200GB → FILE_TOO_LARGE")
    void create_总大小超限时拒绝() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(folder());
        when(fileInfoMapperService.getSubTree("docs/", USER_ID)).thenReturn(List.of(plainFile(201 * GB)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.create(FOLDER_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getHttpStatus());
            assertThat(ex.getMessage()).contains("200GB");
        }
    }

    @Test
    @DisplayName("创建：MQ 投递失败 → 任务置 FAILED，并返回 SYSTEM_ERROR")
    void create_MQ失败时标记失败() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(folder());
        when(fileInfoMapperService.getSubTree("docs/", USER_ID)).thenReturn(List.of(plainFile(1024L)));
        doThrow(new RuntimeException("mq down")).when(folderDownloadMqProducer).sendFolderDownloadTask(anyString());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.create(FOLDER_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getHttpStatus());
        }

        // 任务已经写进 Redis 了，必须补一个失败态，否则前端会一直转圈
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<FolderDownloadTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(redis).update(anyString(), captor.capture());
        FolderDownloadTask real = task(USER_ID, FolderDownloadStatus.PENDING, null, 0L);
        captor.getValue().accept(real);
        assertThat(real.getStatus()).isEqualTo(FolderDownloadStatus.FAILED);
        assertThat(real.getMessage()).contains("MQ任务发送失败");
    }

    @Test
    @DisplayName("创建：正常路径 → 落 Redis、投递 MQ、返回 taskId")
    void create_成功时落库并投递() {
        when(fileInfoMapperService.findByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(folder());
        when(fileInfoMapperService.getSubTree("docs/", USER_ID)).thenReturn(List.of(plainFile(2048L)));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            var vo = service.create(FOLDER_ID);

            assertThat(vo.taskId()).isNotBlank();
            assertThat(vo.status()).isEqualTo(FolderDownloadStatus.PENDING);
            assertThat(vo.totalBytes()).isEqualTo(2048L);
        }
        verify(redis).save(any(FolderDownloadTask.class));
        verify(folderDownloadMqProducer).sendFolderDownloadTask(anyString());
    }

    // ================================================================== 取消

    @Test
    @DisplayName("取消：他人任务 → NO_PERMISSION，且不删别人的临时包")
    void cancel_他人任务时拒绝() {
        when(redis.get(TASK_ID)).thenReturn(
                task(99L, FolderDownloadStatus.SUCCESS, "zip/other.zip", System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.cancel(TASK_ID));

            assertThat(ex.getCode()).isEqualTo(ErrorCode.NO_PERMISSION.getHttpStatus());
        }
        verify(storageService, never()).deleteFile(any());
        verify(redis, never()).update(anyString(), any());
    }

    @Test
    @DisplayName("取消：PENDING → 直接置 CANCELLED（不用等打包线程）")
    void cancel_PENDING直接取消() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.PENDING, null, 0L));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cancel(TASK_ID);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<FolderDownloadTask>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(redis).update(eq(TASK_ID), captor.capture());
        FolderDownloadTask real = task(USER_ID, FolderDownloadStatus.PENDING, null, 0L);
        captor.getValue().accept(real);
        assertThat(real.getStatus()).isEqualTo(FolderDownloadStatus.CANCELLED);
        assertThat(real.isCancelRequested()).isTrue();
    }

    @Test
    @DisplayName("取消：已完成任务 → 删临时压缩包并把 objectKey 置空")
    void cancel_已完成任务删除临时包() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.SUCCESS, "zip/a.zip", System.currentTimeMillis() + 60_000));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cancel(TASK_ID);
        }

        verify(storageService).deleteFile("zip/a.zip");
        // 两次 update：第一次标记取消请求，第二次把 objectKey 清掉
        verify(redis, org.mockito.Mockito.times(2)).update(eq(TASK_ID), any());
    }

    @Test
    @DisplayName("取消：删临时包失败不阻断，仍把任务置为已取消")
    void cancel_删包失败仍置取消() {
        when(redis.get(TASK_ID)).thenReturn(
                task(USER_ID, FolderDownloadStatus.SUCCESS, "zip/a.zip", System.currentTimeMillis() + 60_000));
        doThrow(new RuntimeException("oss error")).when(storageService).deleteFile("zip/a.zip");

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.cancel(TASK_ID);
        }

        verify(redis, org.mockito.Mockito.times(2)).update(eq(TASK_ID), any());
    }
}
