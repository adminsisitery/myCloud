package com.ff.mycloud.service.impl;

import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FileUploadAsyncService;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
public class FileUploadAsyncServiceImpl implements FileUploadAsyncService {

    private final FileService fileService;
    private final UserCenterMapperService userCenterMapperService;
    private final UpLoadProgressService upLoadProgressService;

    public FileUploadAsyncServiceImpl(FileService fileService,
                                      UserCenterMapperService userCenterMapperService,
                                      UpLoadProgressService upLoadProgressService) {
        this.fileService = fileService;
        this.userCenterMapperService = userCenterMapperService;
        this.upLoadProgressService = upLoadProgressService;
    }

    @Async("fileUploadExecutor")
    @Override
    public void asyncUploadFile(String fileType,
                                File tmpFile,
                                String originalFilename,
                                long fileSize,
                                Long parentId,
                                String taskId,
                                Long currentUserId) {
        try {
            if (upLoadProgressService.isCancelled(taskId)) {
                releaseSpaceOnce(taskId, currentUserId, fileSize);
                return;
            }

            upLoadProgressService.markUploading(taskId);
            log.info("Start async file upload, taskId={}, userId={}, fileName={}, size={}",
                    taskId, currentUserId, originalFilename, fileSize);

            fileService.uploadFileWithProgressFromPath(
                    fileType,
                    tmpFile.getAbsolutePath(),
                    originalFilename,
                    fileSize,
                    parentId,
                    taskId,
                    currentUserId
            );

            upLoadProgressService.completeUploadTask(taskId, true, "文件上传成功");
            log.info("Async file upload finished, taskId={}, userId={}, fileName={}",
                    taskId, currentUserId, originalFilename);
        } catch (Exception e) {
            log.error("Async file upload failed, taskId={}, userId={}, fileName={}, releaseSize={}",
                    taskId, currentUserId, originalFilename, fileSize, e);
            releaseSpaceOnce(taskId, currentUserId, fileSize);
            if (!upLoadProgressService.isCancelled(taskId)) {
                upLoadProgressService.completeUploadTask(taskId, false, failureMessage(e));
            }
        } finally {
            deleteTempFile(tmpFile, taskId);
        }
    }

    private void releaseSpaceOnce(String taskId, Long userId, long fileSize) {
        if (upLoadProgressService.tryMarkSpaceReleased(taskId)) {
            userCenterMapperService.releaseSpace(userId, fileSize);
        }
    }

    private void deleteTempFile(File tmpFile, String taskId) {
        if (tmpFile != null && tmpFile.exists() && !tmpFile.delete()) {
            log.warn("Delete temp upload file failed, taskId={}, path={}", taskId, tmpFile.getAbsolutePath());
        }
    }

    private String failureMessage(Exception e) {
        return e.getMessage() == null ? "文件上传失败" : "文件上传失败: " + e.getMessage();
    }
}
