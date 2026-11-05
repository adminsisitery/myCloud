package com.cloudrive.filePreviewService.service.impl;

import com.cloudrive.filePreviewService.config.properties.OssProProperties;
import com.cloudrive.filePreviewService.model.entity.FileInfo;
import com.cloudrive.filePreviewService.model.vo.PreviewVO;
import com.cloudrive.filePreviewService.repositorys.FileInfoMapper;
import com.cloudrive.filePreviewService.service.OssStorageService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PreviewServiceImpl 单元测试：预览信息生成。
 * <p>
 * 对应 TEST-CASES.md 的「PreviewServiceImpl.preview」测试点。
 * 这里唯一的坑是「不泄露文件存在性」：查不到、不是本人、已删除，都必须返回 NO_PERMISSION 而不是 404。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PreviewServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long FILE_ID = 100L;
    private static final String URL = "https://oss.example.com/user_7/a.png?sign=xxx";

    @Mock FileInfoMapper fileInfoMapper;
    @Mock OssStorageService ossStorageService;
    @Mock OssProProperties ossProProperties;

    @InjectMocks PreviewServiceImpl service;

    private FileInfo file(String suffix) {
        FileInfo f = new FileInfo();
        f.setId(FILE_ID);
        f.setUserId(USER_ID);
        f.setFilename("a");
        f.setFileSuffix(suffix);
        f.setPath("user_7/a." + (suffix == null ? "" : suffix));
        f.setFileSize(2048L);
        f.setIsDeleted(false);
        return f;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    @Test
    @DisplayName("preview：查不到记录（不存在/他人/已删除）→ NO_PERMISSION(403)，不泄露存在性")
    void preview_查不到记录时统一403() {
        when(fileInfoMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.preview(FILE_ID));

            assertThat(ex.getCode()).isEqualTo(403);
            assertThat(ex.getMessage()).isEqualTo("无权访问此文件");
        }
        verifyNoInteractions(ossStorageService);
    }

    @Test
    @DisplayName("preview：后缀不支持（含文件夹无后缀）→ FILE_NOT_SUPPORT_PREVIEW(500)")
    void preview_后缀不支持时拒绝() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file(null));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.preview(FILE_ID));

            assertThat(ex.getCode()).isEqualTo(500);
            assertThat(ex.getMessage()).isEqualTo("文件不支持预览");
        }
        verifyNoInteractions(ossStorageService);
    }

    @Test
    @DisplayName("preview：可预览后缀 → 返回 5 分钟预签名 URL 与 MIME")
    void preview_支持的后缀返回预签名地址() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file("png"));
        when(ossStorageService.generatePresignedUrl(anyString(), anyInt(), anyString())).thenReturn(URL);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            PreviewVO vo = service.preview(FILE_ID);

            assertThat(vo.getUrl()).isEqualTo(URL);
            assertThat(vo.getCategory()).isEqualTo("image");
            assertThat(vo.getMimeType()).isEqualTo("image/png");
            // 文件名要补回后缀，前端展示用
            assertThat(vo.getFilename()).isEqualTo("a.png");
            assertThat(vo.getFileSize()).isEqualTo(2048L);
        }

        // 有效期必须是 300 秒（5 分钟），不是随便一个数
        verify(ossStorageService).generatePresignedUrl(eq("user_7/a.png"), eq(300), eq("image/png"));
    }

    @Test
    @DisplayName("preview：文本类文件 → category=text，文件名同样补后缀")
    void preview_文本类文件归类正确() {
        when(fileInfoMapper.selectOne(any())).thenReturn(file("md"));
        when(ossStorageService.generatePresignedUrl(anyString(), anyInt(), anyString())).thenReturn(URL);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            PreviewVO vo = service.preview(FILE_ID);

            assertThat(vo.getCategory()).isEqualTo("text");
            assertThat(vo.getFilename()).isEqualTo("a.md");
        }
    }
}
