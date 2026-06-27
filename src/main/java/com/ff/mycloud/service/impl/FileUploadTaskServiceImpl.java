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

/**
 * 文件上传任务服务实现类
 * <p>管理带进度跟踪的文件上传全流程：
 * 创建任务 -> 校验文件 -> 校验用户和空间 -> 创建临时文件 -> 提交异步上传。
 * 上传失败时自动回滚：释放空间、删除临时文件。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
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

    /**
     * 上传文件并返回进度跟踪任务ID
     * <p>执行流程：
     * 1. 生成任务ID并创建进度记录
     * 2. 校验文件非空
     * 3. 校验用户存在且空间充足
     * 4. 创建临时文件并提交异步上传
     * 5. 异常时回滚释放空间和删除临时文件</p>
     *
     * @param file     上传的文件
     * @param parentId 父目录ID
     * @return 操作结果，成功时data字段为任务ID
     * @throws RuntimeException 当上传提交失败时抛出
     */
    @Override
    public Result<String> uploadWithProgress(MultipartFile file, Long parentId) {
        String taskId = UUID.randomUUID().toString();
        String originalFilename = file == null ? "unknown" : file.getOriginalFilename();
        long fileSize = file == null ? 0L : file.getSize();
        // 创建上传任务进度记录
        upLoadProgressService.createUploadTask(taskId, originalFilename, fileSize);

        if (file == null || file.isEmpty()) {
            upLoadProgressService.completeUploadTask(taskId, false, "文件为空");
            return Result.error("文件为空");
        }

        Long currentUserId = UserContext.getCurrentUserId();
        // 校验用户是否存在
        UserCenter userCenter = userCenterMapperService.getByUserId(currentUserId);
        if (userCenter == null) {
            upLoadProgressService.completeUploadTask(taskId, false, "用户不存在");
            return Result.error("用户不存在");
        }

        // 校验用户可用空间是否足够
        if (!userCenterMapperService.reserveSpace(currentUserId, fileSize)) {
            upLoadProgressService.completeUploadTask(taskId, false, "用户可用空间不足");
            return Result.error("用户可用空间不足");
        }

        File tmpFile = null;
        try {
            // 在系统临时目录创建临时文件
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                throw new IOException("无法创建上传临时目录");
            }

            tmpFile = File.createTempFile(taskId + "_", ".upload", tmpDir);
            // 将上传文件内容写入临时文件
            file.transferTo(tmpFile);

            // 提交异步上传任务
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
            // 上传提交失败：回滚空间并清理临时文件
            releaseSpaceOnce(taskId, currentUserId, fileSize);
            upLoadProgressService.completeUploadTask(taskId, false, failureMessage("上传任务提交失败", e));
            deleteTempFile(tmpFile, taskId);
            throw new RuntimeException("上传任务提交失败", e);
        }
    }

    /**
     * 释放空间（幂等操作，防止重复释放）
     *
     * @param taskId  任务ID
     * @param userId  用户ID
     * @param fileSize 文件大小
     */
    private void releaseSpaceOnce(String taskId, Long userId, long fileSize) {
        if (upLoadProgressService.tryMarkSpaceReleased(taskId)) {
            userCenterMapperService.releaseSpace(userId, fileSize);
        }
    }

    /**
     * 删除临时文件
     *
     * @param tmpFile 临时文件对象
     * @param taskId  任务ID（用于日志记录）
     */
    private void deleteTempFile(File tmpFile, String taskId) {
        if (tmpFile != null && tmpFile.exists() && !tmpFile.delete()) {
            log.warn("Delete temp upload file failed, taskId={}, path={}", taskId, tmpFile.getAbsolutePath());
        }
    }

    /**
     * 构建失败消息
     *
     * @param prefix 消息前缀
     * @param e      异常对象
     * @return 完整的失败消息字符串
     */
    private String failureMessage(String prefix, Exception e) {
        return e.getMessage() == null ? prefix : prefix + ": " + e.getMessage();
    }
}
