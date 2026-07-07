package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.model.vo.TrashListVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import com.ff.mycloud.service.UserCenterMapperService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 回收站功能单元测试
 * <p>覆盖：批量删除、单文件删除、彻底删除、回收站列表、恢复文件、清空回收站</p>
 */
@DisplayName("回收站 DeleteFileService")
class DeleteFileServiceImplTest {

    @Mock private FileInfoMapperService fileInfoMapperService;
    @Mock private RabbitMqService rabbitMqService;
    @Mock private UserCenterMapperService userCenterMapperService;
    @Mock private StorageServiceFactory storageServiceFactory;
    @Mock private StorageService storageService;

    private deleteFileServiceImpl service;
    private MockedStatic<UserContext> userContextMock;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new deleteFileServiceImpl(
                fileInfoMapperService, rabbitMqService,
                userCenterMapperService, storageServiceFactory
        );
        userContextMock = mockStatic(UserContext.class);
        userContextMock.when(UserContext::getCurrentUserId).thenReturn(USER_ID);
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ==================== 辅助方法 ====================
    private FileInfo buildFile(Long id, String name, boolean isFolder, boolean isDeleted) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setFilename(name);
        f.setFileSuffix(isFolder ? null : "txt");
        f.setFileSize(isFolder ? 0L : 100L);
        f.setUserId(USER_ID);
        f.setParentId(0L);
        f.setIsFolder(isFolder);
        f.setIsDeleted(isDeleted);
        f.setLocalFilePath(isFolder ? name + "/" : name + ".txt");
        f.setPath("/user_1/" + name + ".dat");
        f.setSha256Hash("hash-" + id);
        f.setDeletedAt(isDeleted ? LocalDateTime.now() : null);
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return f;
    }

    // ==================== batchDelete ====================
    @Nested
    @DisplayName("batchDeleteFile")
    class BatchDelete {

        @Test
        @DisplayName("所有文件属于当前用户 → 成功软删除")
        void allOwned() {
            FileInfo f1 = buildFile(1L, "a", false, false);
            FileInfo f2 = buildFile(2L, "b", false, false);
            when(fileInfoMapperService.findInIdAndUserId(List.of(1L, 2L), USER_ID))
                    .thenReturn(List.of(f1, f2));
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(500L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.batchDeleteFile(List.of(1L, 2L));

            verify(fileInfoMapperService, times(2)).updateById(any(FileInfo.class));
            verify(userCenterMapperService).updateById(any(UserCenter.class));
            assertEquals(300L, uc.getUsedSpace());
        }

        @Test
        @DisplayName("部分文件不属于当前用户 → 抛 NO_PERMISSION")
        void partialOwned() {
            when(fileInfoMapperService.findInIdAndUserId(List.of(1L, 2L, 3L), USER_ID))
                    .thenReturn(List.of(buildFile(1L, "a", false, false)));

            assertThrows(BusinessException.class,
                    () -> service.batchDeleteFile(List.of(1L, 2L, 3L)));
        }
    }

    // ==================== deleteSingleFile ====================
    @Nested
    @DisplayName("deleteSingleFile")
    class DeleteSingle {

        @Test
        @DisplayName("fileId 为 null → 抛 IllegalArgumentException")
        void nullId() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.deleteSingleFile(null));
        }

        @Test
        @DisplayName("文件不存在 → 抛 RuntimeException")
        void notFound() {
            when(fileInfoMapperService.getById(99L)).thenReturn(null);
            assertThrows(RuntimeException.class,
                    () -> service.deleteSingleFile(99L));
        }

        @Test
        @DisplayName("文件不属于当前用户 → 抛 RuntimeException")
        void notOwned() {
            FileInfo f = buildFile(1L, "other", false, false);
            f.setUserId(999L);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            assertThrows(RuntimeException.class,
                    () -> service.deleteSingleFile(1L));
        }

        @Test
        @DisplayName("删除单文件 → 软删除 + 扣空间")
        void singleFile() {
            FileInfo f = buildFile(1L, "doc", false, false);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(500L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.deleteSingleFile(1L);

            verify(fileInfoMapperService).updateById(f);
            assertTrue(f.getIsDeleted());
            assertNotNull(f.getDeletedAt());
            verify(userCenterMapperService).updateById(any(UserCenter.class));
            assertEquals(400L, uc.getUsedSpace());
        }

        @Test
        @DisplayName("删除文件夹 → 递归获取子树 + 全部软删除")
        void folder() {
            FileInfo folder = buildFile(1L, "folder", true, false);
            folder.setLocalFilePath("folder/");
            FileInfo child = buildFile(2L, "child", false, false);
            child.setLocalFilePath("folder/child.txt");
            when(fileInfoMapperService.getById(1L)).thenReturn(folder);
            when(fileInfoMapperService.getSubTree("folder", USER_ID))
                    .thenReturn(List.of(folder, child));
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(500L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.deleteSingleFile(1L);

            verify(fileInfoMapperService, atLeastOnce()).updateById(any(FileInfo.class));
            assertEquals(400L, uc.getUsedSpace()); // only child counts (100 bytes)
        }

        @Test
        @DisplayName("文件夹路径为空 → 抛 RuntimeException")
        void folderEmptyPath() {
            FileInfo folder = buildFile(1L, "bad", true, false);
            folder.setLocalFilePath(null);
            when(fileInfoMapperService.getById(1L)).thenReturn(folder);
            assertThrows(RuntimeException.class,
                    () -> service.deleteSingleFile(1L));
        }
    }

    // ==================== tureDeleteFile ====================
    @Nested
    @DisplayName("tureDeleteFile（物理删除）")
    class TrueDelete {

        @Test
        @DisplayName("文件不存在 → 跳过（warn）")
        void skipNotFound() {
            when(fileInfoMapperService.getById(99L)).thenReturn(null);
            assertDoesNotThrow(() -> service.tureDeleteFile(List.of(99L)));
            verify(fileInfoMapperService, never()).removeById(anyLong());
        }

        @Test
        @DisplayName("文件不属于当前用户 → 抛 NO_PERMISSION")
        void notOwned() {
            FileInfo f = buildFile(1L, "other", false, true);
            f.setUserId(999L);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            assertThrows(BusinessException.class,
                    () -> service.tureDeleteFile(List.of(1L)));
        }

        @Test
        @DisplayName("文件 refCount > 1 → 不删 OSS，只删数据库")
        void refCountGt1() {
            FileInfo f = buildFile(1L, "shared", false, true);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            when(fileInfoMapperService.countFiles("hash-1")).thenReturn(3L);

            service.tureDeleteFile(List.of(1L));

            verify(storageService, never()).deleteFile(anyString());
            verify(fileInfoMapperService).removeById(1L);
        }

        @Test
        @DisplayName("文件 refCount = 0 → 删 OSS + 删数据库")
        void refCountZero() {
            FileInfo f = buildFile(1L, "alone", false, true);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            when(fileInfoMapperService.countFiles("hash-1")).thenReturn(0L);

            service.tureDeleteFile(List.of(1L));

            verify(storageService).deleteFile(f.getPath());
            verify(fileInfoMapperService).removeById(1L);
        }

        @Test
        @DisplayName("文件夹 → 跳过 OSS，直接删数据库记录")
        void folderSkipOss() {
            FileInfo folder = buildFile(1L, "trashFolder", true, true);
            when(fileInfoMapperService.getById(1L)).thenReturn(folder);

            service.tureDeleteFile(List.of(1L));

            verify(storageService, never()).deleteFile(anyString());
            verify(fileInfoMapperService).removeById(1L);
        }
    }

    // ==================== getTrashList ====================
    @Nested
    @DisplayName("getTrashList")
    class TrashList {

        @Test
        @DisplayName("空回收站 → 返回空列表")
        void empty() {
            when(fileInfoMapperService.getTrashFile(USER_ID))
                    .thenReturn(Collections.emptyList());
            List<TrashListVO> result = service.getTrashList();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("有文件 → 返回 TrashListVO 含 remainingDays")
        void withFiles() {
            FileInfo f = buildFile(1L, "deleted", false, true);
            f.setDeletedAt(LocalDateTime.now().minusDays(5));
            when(fileInfoMapperService.getTrashFile(USER_ID))
                    .thenReturn(List.of(f));

            List<TrashListVO> result = service.getTrashList();
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
            assertEquals("deleted.txt", result.get(0).getFilename());
            assertTrue(result.get(0).getRemainingDays() >= 24
                    && result.get(0).getRemainingDays() <= 25);
        }
    }

    // ==================== restoreFile ====================
    @Nested
    @DisplayName("restoreFile")
    class Restore {

        @Test
        @DisplayName("文件不存在 → 抛 FILE_NOT_FOUND")
        void notFound() {
            when(fileInfoMapperService.getById(99L)).thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> service.restoreFile(99L));
        }

        @Test
        @DisplayName("文件不属于当前用户 → 抛 NO_PERMISSION")
        void notOwned() {
            FileInfo f = buildFile(1L, "other", false, true);
            f.setUserId(999L);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            assertThrows(BusinessException.class,
                    () -> service.restoreFile(1L));
        }

        @Test
        @DisplayName("文件未在回收站 → 抛 FILE_NOT_IN_TRASH")
        void notInTrash() {
            FileInfo f = buildFile(1L, "active", false, false);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            assertThrows(BusinessException.class,
                    () -> service.restoreFile(1L));
        }

        @Test
        @DisplayName("恢复单文件 → 清除 isDeleted + 加回空间")
        void singleFile() {
            FileInfo f = buildFile(1L, "doc", false, true);
            f.setFileSize(200L);
            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(100L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.restoreFile(1L);

            assertFalse(f.getIsDeleted());
            assertNull(f.getDeletedAt());
            assertEquals(300L, uc.getUsedSpace());
        }

        @Test
        @DisplayName("原父目录已被删除 → 恢复到根目录")
        void parentDeleted_moveToRoot() {
            FileInfo f = buildFile(1L, "orphan", false, true);
            f.setParentId(10L);
            f.setLocalFilePath("parentFolder/orphan.txt");
            f.setFileSuffix("txt");

            FileInfo parent = buildFile(10L, "parentFolder", true, true);
            parent.setIsDeleted(true);

            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            when(fileInfoMapperService.getById(10L)).thenReturn(parent);
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(100L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.restoreFile(1L);

            assertEquals(0L, f.getParentId());
            assertEquals("orphan.txt", f.getLocalFilePath());
        }

        @Test
        @DisplayName("原父目录未删除 → 保持原 parentId")
        void parentActive_keepParent() {
            FileInfo f = buildFile(1L, "child", false, true);
            f.setParentId(10L);

            FileInfo parent = buildFile(10L, "alive", true, false);
            parent.setIsDeleted(false);

            when(fileInfoMapperService.getById(1L)).thenReturn(f);
            when(fileInfoMapperService.getById(10L)).thenReturn(parent);
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(100L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.restoreFile(1L);

            assertEquals(10L, f.getParentId());
        }

        @Test
        @DisplayName("恢复文件夹 → 递归恢复子节点")
        void folderRecursive() {
            FileInfo folder = buildFile(1L, "folder", true, true);
            folder.setLocalFilePath("folder/");
            FileInfo child = buildFile(2L, "child", false, true);
            child.setLocalFilePath("folder/child.txt");
            child.setFileSize(100L);

            when(fileInfoMapperService.getById(1L)).thenReturn(folder);
            when(fileInfoMapperService.getById(2L)).thenReturn(child);
            when(fileInfoMapperService.findByParentIdAndUserId(eq(USER_ID), any()))
                    .thenReturn(Collections.emptyList());
            when(fileInfoMapperService.getTrashSubTree("folder/", USER_ID))
                    .thenReturn(List.of(folder, child));
            UserCenter uc = new UserCenter();
            uc.setUsedSpace(100L);
            when(userCenterMapperService.getByUserId(USER_ID)).thenReturn(uc);

            service.restoreFile(1L);

            assertFalse(folder.getIsDeleted());
            assertFalse(child.getIsDeleted());
            assertEquals(200L, uc.getUsedSpace());
        }
    }

    // ==================== cleanTrash ====================
    @Nested
    @DisplayName("cleanTrash")
    class CleanTrash {

        @Test
        @DisplayName("空回收站 → 直接返回")
        void empty() {
            when(fileInfoMapperService.getTrashFile(USER_ID))
                    .thenReturn(Collections.emptyList());
            assertDoesNotThrow(() -> service.cleanTrash());
            verify(fileInfoMapperService, never()).removeById(anyLong());
        }

        @Test
        @DisplayName("有文件 → 物理删除 OSS + 删数据库")
        void withFiles() {
            FileInfo f1 = buildFile(1L, "f1", false, true);
            FileInfo f2 = buildFile(2L, "folder2", true, true);
            when(fileInfoMapperService.getTrashFile(USER_ID))
                    .thenReturn(List.of(f1, f2));
            when(fileInfoMapperService.countFiles("hash-1")).thenReturn(0L);

            service.cleanTrash();

            verify(storageService).deleteFile(f1.getPath());
            verify(storageService, never()).deleteFile(f2.getPath()); // folder
            verify(fileInfoMapperService).removeById(1L);
            verify(fileInfoMapperService).removeById(2L);
        }

        @Test
        @DisplayName("文件 refCount > 1 → 跳过 OSS 删除")
        void refCountGt1_skipOss() {
            FileInfo f = buildFile(1L, "shared", false, true);
            when(fileInfoMapperService.getTrashFile(USER_ID))
                    .thenReturn(List.of(f));
            when(fileInfoMapperService.countFiles("hash-1")).thenReturn(5L);

            service.cleanTrash();

            verify(storageService, never()).deleteFile(anyString());
            verify(fileInfoMapperService).removeById(1L);
        }
    }
}
