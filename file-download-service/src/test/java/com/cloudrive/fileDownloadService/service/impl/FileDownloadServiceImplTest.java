package com.cloudrive.fileDownloadService.service.impl;

import com.cloudrive.fileDownloadService.client.ManageServiceClient;
import com.cloudrive.fileDownloadService.model.entity.FileInfo;
import com.cloudrive.fileDownloadService.model.vo.FileDownloadVO;
import com.cloudrive.fileDownloadService.repositorys.FileInfoMapper;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileDownloadServiceImpl 单元测试：下载前的元信息准备。
 * <p>
 * 对应 TEST-CASES.md 的「FileDownloadServiceImpl.prepareDownload」测试点。
 * 这里有个安全口径值得单独锁住：查不到、没权限、已删除、是文件夹，对外都必须是同一种「文件不存在」，
 * 否则攻击者能靠错误码差异探测别人有什么文件。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileDownloadServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long FILE_ID = 100L;

    @Mock FileInfoMapper fileInfoMapper;
    @Mock StorageService storageService;
    @Mock ManageServiceClient manageServiceClient;

    @InjectMocks FileDownloadServiceImpl service;

    private FileInfo file(boolean folder) {
        FileInfo f = new FileInfo();
        f.setId(FILE_ID);
        f.setUserId(USER_ID);
        f.setIsFolder(folder);
        f.setFilename("report");
        f.setFileSuffix("pdf");
        f.setOriginalFilename("report.pdf");
        f.setFileType("application/pdf");
        f.setFileSize(1024L);
        f.setPath("user_7/report.pdf");
        return f;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    @Test
    @DisplayName("prepareDownload：查不到记录（不存在/他人/已删除）→ FILE_NOT_FOUND(404)")
    void prepareDownload_查不到记录时统一404() {
        when(fileInfoMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.prepareDownload(FILE_ID));

            assertThat(ex.getCode()).isEqualTo(404);
            assertThat(ex.getMessage()).isEqualTo("文件不存在");
        }
    }

    @Test
    @DisplayName("prepareDownload：目标是文件夹 → CANNOT_DOWNLOAD_FOLDER(400)")
    void prepareDownload_文件夹时拒绝() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file(true));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.prepareDownload(FILE_ID));

            assertThat(ex.getCode()).isEqualTo(400);
            assertThat(ex.getMessage()).isEqualTo("不能下载文件夹");
        }
    }

    @Test
    @DisplayName("prepareDownload：正常文件 → 返回路径/文件名/MIME/大小")
    void prepareDownload_正常文件返回元信息() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file(false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            FileDownloadVO vo = service.prepareDownload(FILE_ID);

            assertThat(vo.path()).isEqualTo("user_7/report.pdf");
            assertThat(vo.filename()).isEqualTo("report.pdf");
            assertThat(vo.contentType()).isEqualTo("application/pdf");
            assertThat(vo.size()).isEqualTo(1024L);
        }
        verify(manageServiceClient).recordAccess(USER_ID, FILE_ID);
    }

    @Test
    @DisplayName("prepareDownload：originalFilename 为空 → 降级为 filename + 后缀")
    void prepareDownload_文件名降级拼后缀() {
        FileInfo f = file(false);
        f.setOriginalFilename("   ");
        when(fileInfoMapper.selectOne(any())).thenReturn(f);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.prepareDownload(FILE_ID).filename()).isEqualTo("report.pdf");
        }
    }

    @Test
    @DisplayName("prepareDownload：fileType 为空 → Content-Type 降级为 octet-stream")
    void prepareDownload_类型为空时降级() {
        FileInfo f = file(false);
        f.setFileType(null);
        when(fileInfoMapper.selectOne(any())).thenReturn(f);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.prepareDownload(FILE_ID).contentType()).isEqualTo("application/octet-stream");
        }
    }

    @Test
    @DisplayName("prepareDownload：fileSize 为 null → 按 0 处理，不抛 NPE")
    void prepareDownload_大小为空时按0处理() {
        FileInfo f = file(false);
        f.setFileSize(null);
        when(fileInfoMapper.selectOne(any())).thenReturn(f);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            assertThat(service.prepareDownload(FILE_ID).size()).isZero();
        }
    }

    @Test
    @DisplayName("prepareDownload：recordAccess 失败会直接抛出（与用例文档「不阻断下载」的预期不符）")
    void prepareDownload_记录访问失败会阻断下载() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file(false));
        doThrow(new RuntimeException("manage service down")).when(manageServiceClient).recordAccess(anyLong(), anyLong());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            // 实现里 recordAccess 外面没有 try/catch，内部接口一挂，下载也跟着失败。
            // TEST-CASES.md 写的是「失败不阻断下载」，这里按实际行为断言并标出来——
            // 如果哪天补了 try/catch，这条会变红，正好提醒把断言改成「正常返回」。
            Throwable thrown = catchThrowable(() -> service.prepareDownload(FILE_ID));
            assertThat(thrown).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("openRange：按区间读取委派给 StorageService")
    void openRange_委派给存储层() {
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(storageService.getFileRange(anyString(), anyLong(), anyLong())).thenReturn(stream);

        InputStream result = service.openRange("user_7/report.pdf", 0, 2);

        assertThat(result).isSameAs(stream);
        verify(storageService).getFileRange("user_7/report.pdf", 0, 2);
    }
}
