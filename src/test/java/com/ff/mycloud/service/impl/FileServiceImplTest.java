package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.UserCenter;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileServiceImpl 核心操作单元测试
 * <p>覆盖：移动文件/文件夹、收藏、取消收藏</p>
 */
@DisplayName("FileService 核心操作")
class FileServiceImplTest {

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
    }

    @AfterEach
    void tearDown() {
        userContextMock.close();
    }

    // ==================== 辅助方法 ====================
    private FileInfo buildFile(Long id, String name, boolean isFolder,
                               String suffix, String localFilePath, Long parentId) {
        FileInfo f = new FileInfo();
        f.setId(id);
        f.setFilename(name);
        f.setFileSuffix(suffix);
        f.setFileSize(isFolder ? 0L : 100L);
        f.setUserId(USER_ID);
        f.setParentId(parentId);
        f.setIsFolder(isFolder);
        f.setIsDeleted(false);
        f.setLocalFilePath(localFilePath);
        f.setPath("/user_1/" + name);
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return f;
    }

    // ==================== moveFile ====================
    @Nested
    @DisplayName("moveFile")
    class Move {

        @Test
        @DisplayName("源文件不存在 → 抛 FILE_NOT_FOUND")
        void sourceNotFound() {
            when(fileInfoMapperService.findByIdAndUserId(99L, USER_ID))
                    .thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> service.moveFile(99L, 0L));
        }

        @Test
        @DisplayName("目标目录不存在 → 抛 FILE_NOT_FOUND")
        void targetNotFound() {
            FileInfo source = buildFile(1L, "doc", false, "txt", "doc.txt", 0L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(source);
            when(fileInfoMapperService.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.moveFile(1L, 10L));
        }

        @Test
        @DisplayName("目标不是文件夹 → 抛 BusinessException")
        void targetNotFolder() {
            FileInfo source = buildFile(1L, "doc", false, "txt", "doc.txt", 0L);
            FileInfo target = buildFile(10L, "notFolder", false, "txt", "notFolder.txt", 0L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID)).thenReturn(source);
            when(fileInfoMapperService.findByIdAndUserId(10L, USER_ID)).thenReturn(target);

            assertThrows(BusinessException.class,
                    () -> service.moveFile(1L, 10L));
        }

        @Test
        @DisplayName("文件移动到根目录 → parentId=null, localFilePath=文件名")
        void moveToRoot() {
            FileInfo source = buildFile(1L, "doc", false, "txt",
                    "oldFolder/doc.txt", 5L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(source);

            service.moveFile(1L, 0L);

            assertNull(source.getParentId());
            assertEquals("doc.txt", source.getLocalFilePath());
            verify(fileInfoMapperService).updateById(source);
        }

        @Test
        @DisplayName("文件移动到另一个文件夹 → 更新 parentId + localFilePath")
        void moveToFolder() {
            FileInfo source = buildFile(1L, "doc", false, "txt",
                    "old/doc.txt", 5L);
            FileInfo target = buildFile(10L, "newFolder", true, null,
                    "newFolder/", 0L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID)).thenReturn(source);
            when(fileInfoMapperService.findByIdAndUserId(10L, USER_ID)).thenReturn(target);

            service.moveFile(1L, 10L);

            assertEquals(10L, source.getParentId());
            assertEquals("newFolder/doc.txt", source.getLocalFilePath());
            verify(fileInfoMapperService).updateById(source);
        }

        @Test
        @DisplayName("文件夹移动到自身子树 → 抛 CANNOT_MOVE_INTO_OWN_SUBTREE")
        void circularMove() {
            FileInfo folder = buildFile(1L, "parent", true, null,
                    "parent/", 0L);
            FileInfo child = buildFile(2L, "child", true, null,
                    "parent/child/", 1L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID)).thenReturn(folder);
            when(fileInfoMapperService.findByIdAndUserId(2L, USER_ID)).thenReturn(child);

            // 把 parent 移动到 child 下 = 循环嵌套
            assertThrows(BusinessException.class,
                    () -> service.moveFile(1L, 2L));
        }

        @Test
        @DisplayName("文件夹移动 → 递归更新所有子节点路径")
        void folderRecursive() {
            FileInfo folder = buildFile(1L, "src", true, null,
                    "old/src/", 5L);
            FileInfo target = buildFile(10L, "dst", true, null,
                    "dst/", 0L);
            FileInfo child1 = buildFile(2L, "a", false, "txt",
                    "old/src/a.txt", 1L);
            FileInfo child2 = buildFile(3L, "sub", true, null,
                    "old/src/sub/", 1L);
            FileInfo child3 = buildFile(4L, "b", false, "txt",
                    "old/src/sub/b.txt", 3L);

            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID)).thenReturn(folder);
            when(fileInfoMapperService.findByIdAndUserId(10L, USER_ID)).thenReturn(target);
            when(fileInfoMapperService.getSubTree("old/src/", USER_ID))
                    .thenReturn(List.of(folder, child1, child2, child3));

            service.moveFile(1L, 10L);

            // 根文件夹
            assertEquals(10L, folder.getParentId());
            assertEquals("dst/src/", folder.getLocalFilePath());

            // 子文件
            assertEquals("dst/src/a.txt", child1.getLocalFilePath());
            assertEquals("dst/src/sub/", child2.getLocalFilePath());
            assertEquals("dst/src/sub/b.txt", child3.getLocalFilePath());
        }
    }

    // ==================== star / unstar ====================
    @Nested
    @DisplayName("收藏/取消收藏")
    class Star {

        @Test
        @DisplayName("收藏文件 → 设置 isStarred + starredAt")
        void star() {
            FileInfo f = buildFile(1L, "doc", false, "txt", "doc.txt", 0L);
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(f);

            service.starFile(1L);

            assertTrue(f.getIsStarred());
            assertNotNull(f.getStarredAt());
            verify(fileInfoMapperService).updateById(f);
        }

        @Test
        @DisplayName("收藏不存在的文件 → 抛 FILE_NOT_FOUND")
        void starNotFound() {
            when(fileInfoMapperService.findByIdAndUserId(99L, USER_ID))
                    .thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> service.starFile(99L));
        }

        @Test
        @DisplayName("取消收藏 → 清除 isStarred + starredAt")
        void unstar() {
            FileInfo f = buildFile(1L, "doc", false, "txt", "doc.txt", 0L);
            f.setIsStarred(true);
            f.setStarredAt(LocalDateTime.now());
            when(fileInfoMapperService.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(f);

            service.unstarFile(1L);

            assertFalse(f.getIsStarred());
            assertNull(f.getStarredAt());
            verify(fileInfoMapperService).updateById(f);
        }

        @Test
        @DisplayName("取消收藏不存在的文件 → 抛 FILE_NOT_FOUND")
        void unstarNotFound() {
            when(fileInfoMapperService.findByIdAndUserId(99L, USER_ID))
                    .thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> service.unstarFile(99L));
        }
    }
}
