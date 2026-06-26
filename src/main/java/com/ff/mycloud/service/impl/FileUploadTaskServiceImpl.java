package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.service.FileUploadAsyncService;
import com.ff.mycloud.service.FileUploadTaskService;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class FileUploadTaskServiceImpl implements FileUploadTaskService {

    private final UpLoadProgressService upLoadProgressService;
    private final UserCenterMapperService userCenterMapperService;
    private final FileUploadAsyncService fileUploadAsyncService;

    public FileUploadTaskServiceImpl(UpLoadProgressService upLoadProgressService,
                                     UserCenterMapperService userCenterMapperService,
                                     FileUploadAsyncService fileUploadAsyncService) {
        this.upLoadProgressService = upLoadProgressService;
        this.userCenterMapperService = userCenterMapperService;
        this.fileUploadAsyncService = fileUploadAsyncService;
    }

    @Override
    public Result<String> uploadWithProgress(MultipartFile file, Long parentId) {
        String taskId = UUID.randomUUID().toString();
        String originalFilename = file == null ? "unknown" : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();
        upLoadProgressService.createUploadTask(taskId, originalFilename, fileSize);

        if (file == null || file.isEmpty()) {
            upLoadProgressService.completeUploadTask(taskId, false, "文件为空");
            return Result.error("文件为空");
        }

        Long currentUserId = UserContext.getCurrentUserId();
        UserCenter userCenter = userCenterMapperService.getByUserId(currentUserId);
        if (userCenter == null) {
            upLoadProgressService.completeUploadTask(taskId, false, "用户不存在");
            return Result.error("用户不存在");
        }

        if (!userCenterMapperService.reserveSpace(currentUserId, fileSize)) {
            upLoadProgressService.completeUploadTask(taskId, false, "用户可用空间不足");
            return Result.error("用户可用空间不足");
        }

        File tmpFile = null;
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                throw new IOException("无法创建上传临时目录");
            }

            tmpFile = File.createTempFile(taskId + "_", ".upload", tmpDir);
            file.transferTo(tmpFile);

            fileUploadAsyncService.asyncUploadFile(
                    file.getContentType(),
                    tmpFile,
                    originalFilename,
                    fileSize,
                    parentId,
                    taskId,
                    currentUserId
            );
            return Result.success(taskId);
        } catch (IOException | RuntimeException e) {
            releaseSpaceOnce(taskId, currentUserId, fileSize);
            upLoadProgressService.completeUploadTask(taskId, false, failureMessage("上传任务提交失败", e));
            deleteTempFile(tmpFile, taskId);
            throw new RuntimeException("上传任务提交失败", e);
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

    private String failureMessage(String prefix, Exception e) {
        return e.getMessage() == null ? prefix : prefix + ": " + e.getMessage();
    }
}
