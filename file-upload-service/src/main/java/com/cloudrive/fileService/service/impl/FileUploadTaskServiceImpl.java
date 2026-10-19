package com.cloudrive.fileService.service.impl;

import com.cloudrive.fileService.client.UserSpaceClient;
import com.cloudrive.fileService.service.FileUploadAsyncService;
import com.cloudrive.fileService.service.FileUploadTaskService;
import com.cloudrive.fileService.service.UpLoadProgressService;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 普通文件上传任务服务实现
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.service.impl.FileUploadTaskServiceImpl}。
 * 请求线程只做三件轻量的事：创建 Redis 任务、向用户服务预占配额、把上传流
 * 落成本地临时文件；随后的 OSS 上传与落库交给异步线程池，避免大文件把
 * Tomcat 线程占满。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@Service
public class FileUploadTaskServiceImpl implements FileUploadTaskService {

    private final UpLoadProgressService upLoadProgressService;
    private final UserSpaceClient userSpaceClient;
    private final FileUploadAsyncService fileUploadAsyncService;

    public FileUploadTaskServiceImpl(UpLoadProgressService upLoadProgressService,
                                     UserSpaceClient userSpaceClient,
                                     FileUploadAsyncService fileUploadAsyncService) {
        this.upLoadProgressService = upLoadProgressService;
        this.userSpaceClient = userSpaceClient;
        this.fileUploadAsyncService = fileUploadAsyncService;
    }

    @Override
    public Result<String> uploadWithProgress(MultipartFile file, Long parentId) {
        Long currentUserId = UserContext.getCurrentUserId();

        String taskId = UUID.randomUUID().toString();
        String originalFilename = (file == null || file.getOriginalFilename() == null)
                ? "unknown" : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();

        upLoadProgressService.createUploadTask(taskId, originalFilename, fileSize, currentUserId);

        if (file == null || file.isEmpty()) {
            upLoadProgressService.completeUploadTask(taskId, false, "文件为空");
            return Result.error("文件为空");
        }

        // 配额归属在用户服务，这里只做远程预占；真实扣减由用户服务保证原子性
        if (!userSpaceClient.reserveSpace(currentUserId, fileSize)) {
            upLoadProgressService.completeUploadTask(taskId, false, "用户可用空间不足");
            return Result.error("用户可用空间不足");
        }
        upLoadProgressService.markSpaceReserved(taskId);

        File tmpFile = null;
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                throw new IOException("无法创建上传临时目录: " + tmpDir);
            }
            tmpFile = File.createTempFile(taskId + "_", ".upload", tmpDir);
            file.transferTo(tmpFile);

            fileUploadAsyncService.asyncUploadFile(file.getContentType(), tmpFile, originalFilename,
                    fileSize, parentId, taskId, currentUserId);
            log.info("普通上传任务已提交: taskId={}, userId={}, originalFilename={}, fileSize={}",
                    taskId, currentUserId, originalFilename, fileSize);
            return Result.success(taskId);
        } catch (IOException | RuntimeException e) {
            log.error("普通上传任务提交失败: taskId={}, userId={}", taskId, currentUserId, e);
            try {
                releaseSpaceOnce(taskId, currentUserId, fileSize);
            } catch (RuntimeException releaseEx) {
                // 归还失败时保留 spaceReserved 标记，交给超时扫描任务下一轮补偿
                log.error("提交失败后归还预占空间失败，等待超时扫描补偿: taskId={}", taskId, releaseEx);
            }
            upLoadProgressService.completeUploadTask(taskId, false, failureMessage("上传任务提交失败", e));
            deleteTempFile(tmpFile, taskId);
            throw new RuntimeException("上传任务提交失败", e);
        }
    }

    /**
     * 幂等归还预占空间：先原子占位「已释放」，再调用用户服务；调用失败回滚占位。
     */
    private void releaseSpaceOnce(String taskId, Long userId, long fileSize) {
        if (!upLoadProgressService.tryMarkSpaceReleased(taskId)) {
            return;
        }
        try {
            userSpaceClient.releaseSpace(userId, fileSize);
        } catch (RuntimeException e) {
            upLoadProgressService.unmarkSpaceReleased(taskId);
            throw e;
        }
    }

    private void deleteTempFile(File tmpFile, String taskId) {
        if (tmpFile == null) {
            return;
        }
        try {
            if (tmpFile.exists() && !tmpFile.delete()) {
                log.warn("上传临时文件删除失败: taskId={}, path={}", taskId, tmpFile.getAbsolutePath());
            }
        } catch (RuntimeException e) {
            log.warn("上传临时文件删除异常: taskId={}, path={}", taskId, tmpFile.getAbsolutePath(), e);
        }
    }

    private String failureMessage(String prefix, Exception e) {
        String detail = e.getMessage();
        return (detail == null || detail.isBlank()) ? prefix : prefix + ": " + detail;
    }
}
