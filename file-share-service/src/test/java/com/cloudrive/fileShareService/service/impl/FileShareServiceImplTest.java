package com.cloudrive.fileShareService.service.impl;

import com.cloudrive.fileShareService.mapper.ShareMapper;
import com.cloudrive.fileShareService.model.entity.FileInfo;
import com.cloudrive.fileShareService.model.entity.ShareRecord;
import com.cloudrive.fileShareService.model.vo.ShareFileVO;
import com.cloudrive.fileShareService.redis.ShareQueueRedis;
import com.cloudrive.fileShareService.redis.ShareTokenRedis;
import com.cloudrive.fileShareService.service.FileInfoMapperService;
import com.cloudrive.fileShareService.service.ShareRecordMapperService;
import com.cloudrive.fileShareService.service.StorageService;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileShareServiceImpl 单元测试：创建 / 列表 / 取消 / 访问 / Token 校验。
 * <p>
 * 对应 TEST-CASES.md 的「FileShareServiceImpl」测试点。
 * 分享是最容易出越权的地方，所以失败路径比成功路径更重要：文件不存在、分享不存在、已过期、
 * 有密码但没给密码、密码错、原文件已删——每条都单独锁住。
 * </p>
 * <p>
 * ShareMapper 是 MapStruct 生成的实现类，这里整体 mock 掉：被测的是分支判断，
 * 不是字段映射，映射错了接口测试会发现。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileShareServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String SHARE_CODE = "abc123def456abc123def456abc12345";
    private static final String PASSWORD = "abc123";

    @Mock FileInfoMapperService fileInfoMapperService;
    @Mock ShareMapper shareMapper;
    @Mock ShareRecordMapperService shareRecordMapperService;
    @Mock ShareQueueRedis shareQueueRedis;
    @Mock ShareTokenRedis shareTokenRedis;
    @Mock StorageService storageService;

    @InjectMocks FileShareServiceImpl service;

    // ------------------------------------------------------------------ 夹具

    private FileInfo file(Long id, Long ownerId, boolean folder, boolean deleted) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setUserId(ownerId);
        f.setFilename("doc");
        f.setFileSuffix(folder ? null : "txt");
        f.setFileSize(folder ? null : 1024L);
        f.setIsFolder(folder);
        f.setIsDeleted(deleted);
        return f;
    }

    private ShareRecord record(String password, LocalDateTime expireTime, Boolean expired) {
        ShareRecord r = new ShareRecord();
        r.setId(11L);
        r.setUserId(USER_ID);
        r.setFileId(1L);
        r.setShareCode(SHARE_CODE);
        r.setPassword(password);
        r.setExpireTime(expireTime);
        r.setIsExpired(expired);
        r.setVisitCount(0);
        r.setCreateTime(LocalDateTime.now());
        return r;
    }

    private BusinessException bizExceptionOf(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    private ShareFileVO anyVO() {
        ShareFileVO vo = new ShareFileVO();
        vo.setShareCode(SHARE_CODE);
        return vo;
    }

    // ================================================================== createShare

    @Test
    @DisplayName("createShare：文件不存在 → FILE_NOT_FOUND(404)")
    void createShare_文件不存在时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(
                    () -> service.createShare(1L, LocalDateTime.now().plusDays(1), null));

            assertThat(ex.getCode()).isEqualTo(404);
        }
        verify(shareRecordMapperService, never()).save(any(ShareRecord.class));
    }

    @Test
    @DisplayName("createShare：分享他人文件 → NO_SHARE_PERMISSION(403)")
    void createShare_他人文件时拒绝() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, 99L, false, false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(
                    () -> service.createShare(1L, LocalDateTime.now().plusDays(1), null));

            assertThat(ex.getCode()).isEqualTo(403);
            assertThat(ex.getMessage()).isEqualTo("无权分享此文件");
        }
        verify(shareRecordMapperService, never()).save(any(ShareRecord.class));
    }

    @Test
    @DisplayName("createShare：纯空白密码归一化为无密码")
    void createShare_空白密码按无密码处理() {
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, false));
        when(shareMapper.toShareRecord(anyLong(), anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ShareRecord r = record(invocation.getArgument(3), invocation.getArgument(4), false);
                    return r;
                });
        when(shareMapper.toShareFileVO(any(), anyString(), anyLong(), anyLong(), any())).thenReturn(anyVO());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            service.createShare(1L, LocalDateTime.now().plusDays(1), "   ");
        }

        ArgumentCaptor<String> pwdCaptor = ArgumentCaptor.forClass(String.class);
        verify(shareMapper).toShareRecord(anyLong(), anyLong(), anyString(), pwdCaptor.capture(), any(), any());
        assertThat(pwdCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("createShare：成功 → 落库 + 加入过期延迟队列")
    void createShare_成功时落库并投递延迟队列() {
        LocalDateTime expire = LocalDateTime.now().plusDays(1);
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, false));
        when(shareMapper.toShareRecord(anyLong(), anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ShareRecord r = record(invocation.getArgument(3), invocation.getArgument(4), false);
                    r.setId(11L);
                    return r;
                });
        when(shareMapper.toShareFileVO(any(), anyString(), anyLong(), anyLong(), any())).thenReturn(anyVO());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            ShareFileVO vo = service.createShare(1L, expire, PASSWORD);
            assertThat(vo.getShareCode()).isEqualTo(SHARE_CODE);
        }

        verify(shareRecordMapperService).save(any(ShareRecord.class));
        verify(shareQueueRedis).addToDelayedQueue(eq(11L), eq(expire));
    }

    // ================================================================== getSharedFiles

    @Test
    @DisplayName("getSharedFiles：关联文件已删除的记录被跳过")
    void getSharedFiles_跳过已删除文件() {
        ShareRecord alive = record(null, LocalDateTime.now().plusDays(1), false);
        ShareRecord broken = record(null, LocalDateTime.now().plusDays(1), false);
        broken.setId(12L);
        broken.setFileId(2L);

        when(shareRecordMapperService.findByUserIdAndNotDel(USER_ID)).thenReturn(List.of(alive, broken));
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, false));
        when(fileInfoMapperService.getById(2L)).thenReturn(file(2L, USER_ID, false, true));   // 已删除
        when(shareMapper.toShareFileVO(any(), anyString(), anyLong(), anyLong(), any())).thenReturn(anyVO());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            List<ShareFileVO> list = service.getSharedFiles();

            assertThat(list).hasSize(1);
        }
    }

    @Test
    @DisplayName("getSharedFiles：文件夹分享的 fileSize 固定为 0")
    void getSharedFiles_文件夹分享大小为0() {
        when(shareRecordMapperService.findByUserIdAndNotDel(USER_ID))
                .thenReturn(List.of(record(null, LocalDateTime.now().plusDays(1), false)));
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, true, false));
        when(shareMapper.toShareFileVO(any(), anyString(), anyLong(), anyLong(), any())).thenReturn(anyVO());

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
            service.getSharedFiles();
        }

        verify(shareMapper).toShareFileVO(any(), eq("doc"), eq(0L), eq(1L), eq(true));
    }

    // ================================================================== validateShareToken

    @Test
    @DisplayName("validateShareToken：分享不存在 → false")
    void validateToken_分享不存在时返回false() {
        when(shareRecordMapperService.findByShareCode("nope")).thenReturn(null);

        assertThat(service.validateShareToken("nope", "any")).isFalse();
        verify(shareTokenRedis, never()).validateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("validateShareToken：无密码分享 → 直接 true，不查 Redis")
    void validateToken_无密码分享直接放行() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(null, LocalDateTime.now().plusDays(1), false));

        assertThat(service.validateShareToken(SHARE_CODE, null)).isTrue();
        verify(shareTokenRedis, never()).validateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("validateShareToken：有密码分享 → 委派给 Redis 校验")
    void validateToken_有密码分享委托Redis() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(PASSWORD, LocalDateTime.now().plusDays(1), false));
        when(shareTokenRedis.validateToken(SHARE_CODE, "tok")).thenReturn(true);

        assertThat(service.validateShareToken(SHARE_CODE, "tok")).isTrue();
        verify(shareTokenRedis).validateToken(SHARE_CODE, "tok");
    }

    // ================================================================== accessShare

    @Test
    @DisplayName("accessShare：分享不存在 → SHARE_NOT_FOUND(404)")
    void accessShare_分享不存在时拒绝() {
        when(shareRecordMapperService.findByShareCode("nope")).thenReturn(null);

        BusinessException ex = bizExceptionOf(() -> service.accessShare("nope", null));

        assertThat(ex.getCode()).isEqualTo(404);
        assertThat(ex.getMessage()).isEqualTo("分享链接不存在");
    }

    @Test
    @DisplayName("accessShare：isExpired 已标记 → SHARE_EXPIRED(410)")
    void accessShare_已标记过期时拒绝() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(null, LocalDateTime.now().plusDays(1), true));

        BusinessException ex = bizExceptionOf(() -> service.accessShare(SHARE_CODE, null));

        assertThat(ex.getCode()).isEqualTo(410);
        assertThat(ex.getMessage()).isEqualTo("分享链接已过期");
    }

    @Test
    @DisplayName("accessShare：expireTime 早于当前时间 → SHARE_EXPIRED(410)")
    void accessShare_过期时间已到时拒绝() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(null, LocalDateTime.now().minusMinutes(1), false));

        BusinessException ex = bizExceptionOf(() -> service.accessShare(SHARE_CODE, null));

        assertThat(ex.getCode()).isEqualTo(410);
    }

    @Test
    @DisplayName("accessShare：有密码但未传 → MISSING_PASSWORD(401)")
    void accessShare_缺少密码时拒绝() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(PASSWORD, LocalDateTime.now().plusDays(1), false));

        BusinessException ex = bizExceptionOf(() -> service.accessShare(SHARE_CODE, null));

        assertThat(ex.getCode()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("请提供访问密码");
    }

    @Test
    @DisplayName("accessShare：密码错误 → INVALID_PASSWORD(401)")
    void accessShare_密码错误时拒绝() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(PASSWORD, LocalDateTime.now().plusDays(1), false));

        BusinessException ex = bizExceptionOf(() -> service.accessShare(SHARE_CODE, "wrong1"));

        assertThat(ex.getCode()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("密码错误");
    }

    @Test
    @DisplayName("accessShare：关联文件已删除 → FILE_NOT_FOUND(404)")
    void accessShare_原文件已删除时拒绝() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(PASSWORD, LocalDateTime.now().plusDays(1), false));
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, true));

        BusinessException ex = bizExceptionOf(() -> service.accessShare(SHARE_CODE, PASSWORD));

        assertThat(ex.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("accessShare：成功 → 访问次数 +1，返回分享信息")
    void accessShare_成功时累加访问次数() {
        when(shareRecordMapperService.findByShareCode(SHARE_CODE))
                .thenReturn(record(PASSWORD, LocalDateTime.now().plusDays(1), false));
        when(fileInfoMapperService.getById(1L)).thenReturn(file(1L, USER_ID, false, false));
        when(shareMapper.toShareFileVO(any(), anyString(), anyLong(), anyLong(), any())).thenReturn(anyVO());

        ShareFileVO vo = service.accessShare(SHARE_CODE, PASSWORD);

        assertThat(vo.getShareCode()).isEqualTo(SHARE_CODE);
        // 计数用「读到的旧值」+ 记录 ID，只调用一次
        verify(shareRecordMapperService).visitedPlus1(0L, 11L);
    }

    // ================================================================== cancelShare

    @Test
    @DisplayName("cancelShare：记录不存在（含他人分享）→ SHARE_NOT_FOUND(404)")
    void cancelShare_记录不存在时拒绝() {
        when(shareRecordMapperService.findByUserIdAndShareCode(USER_ID, SHARE_CODE)).thenReturn(null);

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            BusinessException ex = bizExceptionOf(() -> service.cancelShare(SHARE_CODE));

            assertThat(ex.getCode()).isEqualTo(404);
        }
        verify(shareRecordMapperService, never()).removeById(anyLong());
    }

    @Test
    @DisplayName("cancelShare：成功 → 移出延迟队列 + 删库 + 删 Token")
    void cancelShare_成功时三处都清理() {
        when(shareRecordMapperService.findByUserIdAndShareCode(USER_ID, SHARE_CODE))
                .thenReturn(record(null, LocalDateTime.now().plusDays(1), false));

        try (MockedStatic<UserContext> ctx = mockStatic(UserContext.class)) {
            ctx.when(UserContext::getCurrentUserId).thenReturn(USER_ID);

            service.cancelShare(SHARE_CODE);
        }

        verify(shareQueueRedis).removeFromDelayedQueue(11L);
        verify(shareRecordMapperService).removeById(11L);
        verify(shareTokenRedis).deleteToken(SHARE_CODE);
    }
}
