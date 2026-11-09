package com.cloudrive.aiService.service.impl;

import com.cloudrive.aiService.client.UserSpaceClient;
import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.mq.MqProducer;
import com.cloudrive.aiService.service.FileInfoMapperService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.domain.mq.PgIndexMessage;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent 动作的索引同步。
 * <p>
 * 守的是一个很容易漏、也很难自查的点：ai-service 和 file-manage-service 都写
 * {@code t_file_info}，但只有前者以前不发旁路消息。结果是「Agent 删掉的文件
 * 还能被搜到」「Agent 新建的文档搜不到」——接口全部返回成功，没有任何报错，
 * 只有用户在检索里找不到东西时才会发现。
 * </p>
 * <p>
 * 交易边界不在这里测（无 Spring 容器时不注册同步器，回调立即执行），
 * 这里只锁「有没有发、发的是哪个动作」。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@ExtendWith(MockitoExtension.class)
class DeleteFileIndexSyncTest {

    private static final Long USER_ID = 7L;
    private static final long SIZE = 2048L;

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock UserSpaceClient userSpaceClient;
    @Mock MqProducer mqProducer;

    @InjectMocks DeleteFileServiceImpl service;

    private FileInfo file(boolean deleted) {
        FileInfo f = new FileInfo();
        f.setId(1L);
        f.setUserId(USER_ID);
        f.setFilename("report");
        f.setFileSuffix("pdf");
        f.setFileType("application/pdf");
        f.setFileSize(SIZE);
        f.setSha256Hash("hash-1");
        f.setIsFolder(false);
        f.setIsDeleted(deleted);
        f.setParentId(0L);
        f.setLocalFilePath("report.pdf");
        f.setPath("user_7/report.pdf");
        return f;
    }

    @Test
    @DisplayName("Agent 删除文件 → 投递 ES 删除 + PG 删除 + 操作日志")
    void 删除后同步索引() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.deleteSingleFile(1L);
        }

        verify(mqProducer).sendIndexDelete(any(FileIndexMessage.class));
        verify(mqProducer).sendPgDelete(any(PgIndexMessage.class));
        verify(mqProducer).sendLog(any(OperationLogMessage.class));
        verify(mqProducer, never()).sendIndexInsert(any(FileIndexMessage.class));
    }

    @Test
    @DisplayName("Agent 恢复文件 → 投递 ES 插入 + PG 重建")
    void 恢复后重建索引() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.restoreFile(1L);
        }

        verify(mqProducer).sendIndexInsert(any(FileIndexMessage.class));
        verify(mqProducer).sendPgInsert(any(PgIndexMessage.class));
        verify(mqProducer, never()).sendIndexDelete(any(FileIndexMessage.class));
    }

    @Test
    @DisplayName("已在回收站的文件再删一次 → 拒绝且不发任何索引消息")
    void 重复删除不发消息() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            Throwable thrown = catchThrowable(() -> service.deleteSingleFile(1L));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) thrown).getCode()).isEqualTo(404);
        }

        verifyNoInteractions(mqProducer);
        verify(userSpaceClient, never()).releaseSpace(anyLong(), anyLong());
    }
}
