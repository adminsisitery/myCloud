package com.ff.mycloud.service.impl;

import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadAsyncServiceImplTest {

    private static final String TASK_ID = "task-1";
    private static final Long USER_ID = 1L;
    private static final long FILE_SIZE = 100L;

    @Test
    void successfulUploadShouldCompleteWithoutReleasingSpace() throws IOException {
        FileService fileService = mock(FileService.class);
        UserCenterMapperService userCenterService = mock(UserCenterMapperService.class);
        UpLoadProgressService progressService = mock(UpLoadProgressService.class);
        File tempFile = createTempFile();

        FileUploadAsyncServiceImpl service =
                new FileUploadAsyncServiceImpl(fileService, userCenterService, progressService);
        service.asyncUploadFile("application/octet-stream", tempFile, "demo.bin",
                FILE_SIZE, null, TASK_ID, USER_ID);

        verify(progressService).markUploading(TASK_ID);
        verify(progressService).completeUploadTask(TASK_ID, true, "文件上传成功");
        verify(userCenterService, never()).releaseSpace(USER_ID, FILE_SIZE);
        assertFalse(tempFile.exists());
    }

    @Test
    void failedUploadShouldReleaseSpaceAndMarkFailed() throws IOException {
        FileService fileService = mock(FileService.class);
        UserCenterMapperService userCenterService = mock(UserCenterMapperService.class);
        UpLoadProgressService progressService = mock(UpLoadProgressService.class);
        File tempFile = createTempFile();

        doThrow(new RuntimeException("OSS unavailable"))
                .when(fileService)
                .uploadFileWithProgressFromPath(
                        anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.nullable(Long.class), anyString(),
                        org.mockito.ArgumentMatchers.anyLong());
        when(progressService.tryMarkSpaceReleased(TASK_ID)).thenReturn(true);

        FileUploadAsyncServiceImpl service =
                new FileUploadAsyncServiceImpl(fileService, userCenterService, progressService);
        service.asyncUploadFile("application/octet-stream", tempFile, "demo.bin",
                FILE_SIZE, null, TASK_ID, USER_ID);

        verify(userCenterService).releaseSpace(USER_ID, FILE_SIZE);
        verify(progressService).completeUploadTask(
                org.mockito.ArgumentMatchers.eq(TASK_ID),
                org.mockito.ArgumentMatchers.eq(false),
                anyString());
        assertFalse(tempFile.exists());
    }

    @Test
    void cancelledTaskShouldReleaseOnlyReservedSpaceAndSkipUpload() throws IOException {
        FileService fileService = mock(FileService.class);
        UserCenterMapperService userCenterService = mock(UserCenterMapperService.class);
        UpLoadProgressService progressService = mock(UpLoadProgressService.class);
        File tempFile = createTempFile();

        when(progressService.isCancelled(TASK_ID)).thenReturn(true);
        when(progressService.tryMarkSpaceReleased(TASK_ID)).thenReturn(true);

        FileUploadAsyncServiceImpl service =
                new FileUploadAsyncServiceImpl(fileService, userCenterService, progressService);
        service.asyncUploadFile("application/octet-stream", tempFile, "demo.bin",
                FILE_SIZE, null, TASK_ID, USER_ID);

        verify(fileService, never()).uploadFileWithProgressFromPath(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.nullable(Long.class), anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(userCenterService).releaseSpace(USER_ID, FILE_SIZE);
        verify(progressService, never()).completeUploadTask(anyString(), anyBoolean(), anyString());
        assertFalse(tempFile.exists());
    }

    private File createTempFile() throws IOException {
        File tempFile = Files.createTempFile("upload-test-", ".tmp").toFile();
        tempFile.deleteOnExit();
        return tempFile;
    }
}
