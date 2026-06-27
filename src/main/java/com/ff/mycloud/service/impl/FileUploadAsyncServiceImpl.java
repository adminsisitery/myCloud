package com.ff.mycloud.service.impl;

import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FileUploadAsyncService;
import com.ff.mycloud.service.UpLoadProgressService;
import com.ff.mycloud.service.UserCenterMapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 文件异步上传服务实现类
 * <p>使用Spring {@code @Async("fileUploadExecutor")}线程池异步执行文件上传。
 * 上传前检查任务是否已取消，上传完成后标记任务状态。
 * 异常时自动回滚：释放用户空间、清理临时文件。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
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

    /**
     * 异步执行文件上传
     * <p>执行流程：
     * 1. 检查任务是否已取消（取消则释放空间并返回）
     * 2. 标记为上传中状态
     * 3. 调用文件服务执行实际上传
     * 4. 上传成功则标记任务完成
     * 5. 异常时释放空间、标记任务失败
     * 6. finally中清理临时文件</p>
     *
     * @param fileType         文件MIME类型
     * @param tmpFile          临时文件对象
     * @param originalFilename 原始文件名
     * @param fileSize         文件大小（字节）
     * @param parentId         父目录ID
     * @param taskId           上传任务ID
     * @param currentUserId    当前用户ID
     */
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
            // 检查任务是否已被用户取消
            if (upLoadProgressService.isCancelled(taskId)) {
                releaseSpaceOnce(taskId, currentUserId, fileSize);
                return;
            }

            upLoadProgressService.markUploading(taskId);
            log.info("Start async file upload, taskId={}, userId={}, fileName={}, size={}",
                    taskId, currentUserId, originalFilename, fileSize);

            // 执行文件上传（含秒传检测、存储、ES同步、操作日志）
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
            // 上传失败：释放已预留的空间
            releaseSpaceOnce(taskId, currentUserId, fileSize);
            if (!upLoadProgressService.isCancelled(taskId)) {
                upLoadProgressService.completeUploadTask(taskId, false, failureMessage(e));
            }
        } finally {
            // 无论成功失败，清理临时文件
            deleteTempFile(tmpFile, taskId);
        }
    }

    /**
     * 释放用户空间（幂等操作）
     *
     * @param taskId   任务ID
     * @param userId   用户ID
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
     * @param tmpFile 临时文件
     * @param taskId  任务ID（日志记录用）
     */
    private void deleteTempFile(File tmpFile, String taskId) {
        if (tmpFile != null && tmpFile.exists() && !tmpFile.delete()) {
            log.warn("Delete temp upload file failed, taskId={}, path={}", taskId, tmpFile.getAbsolutePath());
        }
    }

    /**
     * 构建失败消息
     *
     * @param e 异常对象
     * @return 失败描述字符串
     */
    private String failureMessage(Exception e) {
        return e.getMessage() == null ? "文件上传失败" : "文件上传失败: " + e.getMessage();
    }
}
