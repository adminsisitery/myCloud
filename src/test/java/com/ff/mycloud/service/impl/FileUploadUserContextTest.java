package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.*;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileUploadUserContextTest {
    @TempDir Path directory;
    private final FileInfoMapperService files = mock(FileInfoMapperService.class);
    private final UserMapperServiceImpl users = mock(UserMapperServiceImpl.class);
    private final UserCenterMapperService centers = mock(UserCenterMapperService.class);
    private final StorageServiceFactory storageFactory = mock(StorageServiceFactory.class);
    private final StorageService storage = mock(StorageService.class);
    private final FileMapper mapper = mock(FileMapper.class);
    private final RabbitMqService mq = mock(RabbitMqService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private FileServiceImpl service;
    private Path upload;

    @BeforeEach
    void setup() throws Exception {
        service = new FileServiceImpl(storageFactory, centers, mapper, files,
                mock(UserContext.class), users, mock(UpLoadProgressService.class), mq,
                mock(FileObjectMapper.class), null, mock(FileRecentAccess.class), transactions);
        User user = new User();
        user.setId(21L);
        when(users.findOneByUserId(21L)).thenReturn(user);
        when(files.findByParentIdAndUserId(21L, 0L)).thenReturn(List.of());
        upload = Files.write(directory.resolve("sample.bin"), new byte[]{1, 2, 3, 4});
    }

    @Test
    void normalUploadUsesTaskOwnerForQuotaAndAuditWithoutLoginContext() throws Exception {
        when(files.findByHashAndUserId(anyString(), eq(21L))).thenReturn(List.of());
        when(storageFactory.getStorageService()).thenReturn(storage);
        when(storage.uploadFileWithProgressFromPath(any(), anyString(), eq("task-21"),
                eq("sample.bin"), eq(4L), eq("application/octet-stream"))).thenReturn("user_21/object.bin");
        FileInfo saved = file(101L);
        when(mapper.toFileInfoFromPath(anyString(), anyString(), eq(4L), eq(21L), eq(0L),
                anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(saved);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        UserCenter center = new UserCenter();
        center.setUsedSpace(95L);
        center.setTotalSpace(100L);
        when(centers.getByUserId(21L)).thenReturn(center);

        runOnWorker();

        verify(centers).getByUserId(21L);
        verify(mq).UsageChanged(argThat(message -> Long.valueOf(21L).equals(message.getUserId())
                && message.getUsageSize() == 95L));
        verify(mq).sendLogMessage(argThat(entry -> Long.valueOf(21L).equals(entry.getUserId())
                && Long.valueOf(101L).equals(entry.getFileId())));
        verify(mq).sendInsertMessage(argThat(document -> Long.valueOf(21L).equals(document.getUserId())));
        verify(storage, never()).deleteFile(anyString());
    }

    @Test
    void fastUploadUsesTaskOwnerForAuditWithoutLoginContext() throws Exception {
        FileInfo existing = file(100L);
        when(files.findByHashAndUserId(anyString(), eq(21L))).thenReturn(List.of(existing));
        FileInfo copy = file(102L);
        when(mapper.toFileInfoForFastUpload(eq("sample.bin"), eq("sample"), eq(existing),
                eq(21L), eq(0L), anyString())).thenReturn(copy);

        runOnWorker();

        verify(files).save(copy);
        verify(mq).sendLogMessage(argThat(entry -> Long.valueOf(21L).equals(entry.getUserId())
                && Long.valueOf(102L).equals(entry.getFileId()) && "秒传".equals(entry.getExtraInfo())));
        verify(mq).sendInsertMessage(argThat(document -> Long.valueOf(21L).equals(document.getUserId())));
        verifyNoInteractions(storageFactory);
    }

    private void runOnWorker() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> {
                assertNull(RequestContextHolder.getRequestAttributes());
                service.uploadFileWithProgressFromPath("application/octet-stream", upload.toString(),
                        "sample.bin", 4L, 0L, "task-21", 21L);
                return null;
            });
            result.get(10, TimeUnit.SECONDS);
        }
    }

    private FileInfo file(Long id) {
        FileInfo file = new FileInfo();
        file.setId(id);
        file.setUserId(21L);
        file.setFilename("sample");
        file.setFileSuffix("bin");
        file.setFileType("application/octet-stream");
        file.setFileSize(4L);
        file.setPath("user_21/object.bin");
        return file;
    }
}
