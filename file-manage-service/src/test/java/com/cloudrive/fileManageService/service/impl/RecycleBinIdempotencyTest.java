package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.client.UserSpaceClient;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.mq.MqProducer;
import com.cloudrive.fileManageService.repositorys.FileObjectMapper;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.StorageService;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配额账目的幂等性约束。
 * <p>
 * 这两个用例守的是同一件事：<b>同一个文件被重复删除 / 重复恢复时，配额只能被调整一次</b>。
 * 判断依据全部落在 user-space 的远程调用次数上，不依赖数据库或 Redis。
 * </p>
 * <p>
 * 为什么单独一个类：这类问题的表现是「账不平」，不会抛异常、不会让接口在正常路径上报错，
 * 只会让用户的可用空间慢慢偏离真实值。放在功能用例里很容易被当成实现细节忽略。
 * </p>
 * <p>
 * 也是回归护栏：删除侧的 isDeleted 判断在拆服务时丢过一次，
 * 结果同一个文件删两次就退还两次配额。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@ExtendWith(MockitoExtension.class)
class RecycleBinIdempotencyTest {

    private static final Long USER_ID = 7L;
    private static final long SIZE = 1024L;

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock FileObjectMapper fileObjectMapper;
    @Mock UserSpaceClient userSpaceClient;
    @Mock StorageService storageService;
    @Mock MqProducer mqProducer;

    @InjectMocks RecycleBinServiceImpl service;

    private FileInfo file(boolean deleted) {
        FileInfo f = new FileInfo();
        f.setId(1L);
        f.setUserId(USER_ID);
        f.setIsFolder(false);
        f.setFileSize(SIZE);
        f.setIsDeleted(deleted);
        f.setFilename("doc");
        f.setFileSuffix("txt");
        f.setParentId(0L);
        f.setLocalFilePath("doc.txt");
        f.setPath("user_7/doc.txt");
        f.setSha256Hash("hash-1");
        return f;
    }

    @Test
    @DisplayName("deleteSingleFile：对已删除的文件再删一次 → 拒绝，且不再退还一次配额")
    void 重复删除不得重复退还配额() {
        // 模拟用户点了两次删除：第一次把文件置为已删除，第二次读到的就是 isDeleted=true 的同一行
        when(fileInfoMapperService.getById(1L)).thenReturn(file(false), file(true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            service.deleteSingleFile(1L);
            Throwable second = catchThrowable(() -> service.deleteSingleFile(1L));

            assertThat(second).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) second).getCode()).isEqualTo(404);
        }

        verify(userSpaceClient, times(1)).releaseSpace(USER_ID, SIZE);
    }

    @Test
    @DisplayName("restoreFile：对已恢复的文件再恢复一次 → 拒绝，且不再占用一次配额")
    void 重复恢复不得重复占用配额() {
        // 已经恢复过的行 isDeleted=false，再调一次恢复不应该再加一遍 used_space
        when(fileInfoMapperService.getById(1L)).thenReturn(file(true), file(false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            service.restoreFile(1L);
            Throwable second = catchThrowable(() -> service.restoreFile(1L));

            assertThat(second).isInstanceOf(BusinessException.class);
        }

        verify(userSpaceClient, times(1)).addUsedSpace(USER_ID, SIZE);
    }

    @Test
    @DisplayName("deleteSingleFile：他人已删除的文件 → 仍先报无权限，不泄露存在性")
    void 他人已删除文件按无权限处理() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(99L);

            Throwable thrown = catchThrowable(() -> service.deleteSingleFile(1L));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getCode()).isEqualTo(403);
        }

        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }
}
