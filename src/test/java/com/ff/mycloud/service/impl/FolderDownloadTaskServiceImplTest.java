package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.FolderDownloadStatus;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.FolderDownloadTask;
import com.ff.mycloud.model.vo.FolderDownloadTaskVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.redis.FolderDownloadRedis;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FolderService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FolderDownloadTaskServiceImplTest {
    @Mock private FolderDownloadRedis redis;
    @Mock private FileInfoMapperService fileInfoService;
    @Mock private FolderService folderService;
    @Mock private StorageServiceFactory storageFactory;
    @Mock private StorageService storage;
    @Mock private RabbitMqService rabbitMqService;
    @Mock private RLock processingLock;

    private FolderDownloadTaskServiceImpl service;
    private MockedStatic<UserContext> userContext;

    @BeforeEach
    void setUp() throws InterruptedException {
        MockitoAnnotations.openMocks(this);
        service = new FolderDownloadTaskServiceImpl(redis, fileInfoService, folderService,
                storageFactory, rabbitMqService);
        when(storageFactory.getStorageService()).thenReturn(storage);
        when(redis.processingLock(anyString())).thenReturn(processingLock);
        when(processingLock.tryLock()).thenReturn(true);
        when(processingLock.isHeldByCurrentThread()).thenReturn(true);
        userContext = mockStatic(UserContext.class);
        userContext.when(UserContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        userContext.close();
    }

    @Test
    void createPersistsPendingTaskAndPublishesMessage() {
        FileInfo root = folder(10L, "资料", "资料/");
        FileInfo child = new FileInfo();
        child.setIsFolder(false);
        child.setFileSize(4096L);
        when(fileInfoService.findByIdAndUserId(10L, 1L)).thenReturn(root);
        when(fileInfoService.getSubTree("资料/", 1L)).thenReturn(List.of(root, child));

        FolderDownloadTaskVO result = service.create(10L);

        assertEquals(FolderDownloadStatus.PENDING, result.status());
        assertEquals(4096, result.totalBytes());
        verify(redis).save(argThat(t -> t.getUserId().equals(1L) && t.getFolderId().equals(10L)));
        verify(rabbitMqService).sendFolderDownloadTask(result.taskId());
    }

    @Test
    void consumerPackagesUploadsAndMarksSuccess() throws Exception {
        FolderDownloadTask task = task("task-1", FolderDownloadStatus.PENDING);
        when(redis.get("task-1")).thenReturn(task);
        mockInPlaceUpdate(task);
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(2);
            output.write("zip-content".getBytes());
            java.util.function.LongConsumer progress = invocation.getArgument(3);
            progress.accept(100);
            return null;
        }).when(folderService).writeFolderArchive(eq(10L), eq(1L), any(OutputStream.class), any(), any());

        service.process("task-1");

        assertEquals(FolderDownloadStatus.SUCCESS, task.getStatus());
        assertEquals(1.0, task.getProgress());
        assertNotNull(task.getObjectKey());
        assertTrue(task.getExpiresAt() > System.currentTimeMillis());
        verify(storage).uploadObject(contains("temp/folder-download/user_1/task-1.zip"),
                any(), eq(11L), eq("application/zip"));
    }

    @Test
    void duplicateSuccessMessageIsIdempotentlyIgnored() {
        FolderDownloadTask task = task("task-2", FolderDownloadStatus.SUCCESS);
        when(redis.get("task-2")).thenReturn(task);

        service.process("task-2");

        verifyNoInteractions(folderService);
        verify(storage, never()).uploadObject(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void cancellingCompletedTaskDeletesTemporaryObject() {
        FolderDownloadTask task = task("task-3", FolderDownloadStatus.SUCCESS);
        task.setObjectKey("temp/archive.zip");
        when(redis.get("task-3")).thenReturn(task);
        mockInPlaceUpdate(task);

        service.cancel("task-3");

        verify(storage).deleteFile("temp/archive.zip");
        assertEquals(FolderDownloadStatus.CANCELLED, task.getStatus());
        assertNull(task.getObjectKey());
    }

    @SuppressWarnings("unchecked")
    private void mockInPlaceUpdate(FolderDownloadTask task) {
        when(redis.update(eq(task.getTaskId()), any())).thenAnswer(invocation -> {
            Consumer<FolderDownloadTask> updater = invocation.getArgument(1);
            updater.accept(task);
            return task;
        });
    }

    private FolderDownloadTask task(String id, FolderDownloadStatus status) {
        FolderDownloadTask task = new FolderDownloadTask();
        task.setTaskId(id);
        task.setUserId(1L);
        task.setFolderId(10L);
        task.setFolderName("资料");
        task.setStatus(status);
        task.setTotalBytes(100);
        task.setCreatedAt(System.currentTimeMillis());
        task.setUpdatedAt(System.currentTimeMillis());
        return task;
    }

    private FileInfo folder(Long id, String name, String path) {
        FileInfo file = new FileInfo();
        file.setId(id);
        file.setUserId(1L);
        file.setFilename(name);
        file.setLocalFilePath(path);
        file.setIsFolder(true);
        file.setIsDeleted(false);
        return file;
    }
}
