package com.cloudrive.fileDownloadService.service.impl;

import com.cloudrive.fileDownloadService.model.entity.FileInfo;
import com.cloudrive.fileDownloadService.model.vo.FolderDownloadTaskVO;
import com.cloudrive.fileDownloadService.mq.FolderDownloadMqProducer;
import com.cloudrive.fileDownloadService.redis.FolderDownloadRedis;
import com.cloudrive.fileDownloadService.service.FileInfoMapperService;
import com.cloudrive.fileDownloadService.service.FolderDownloadTaskService;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.domain.folder.FolderDownloadStatus;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 文件夹异步打包任务服务实现（下载服务侧）
 * <p>
 * 逻辑从原单体 {@code FolderDownloadTaskServiceImpl#create/getOwnedTask/
 * getDownloadableTask/cancel} 迁移而来；process/cleanupExpired 已在
 * file-async-service 中实现。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FolderDownloadTaskServiceImpl implements FolderDownloadTaskService {

    private static final Logger logger = LoggerFactory.getLogger(FolderDownloadTaskServiceImpl.class);

    /** 压缩包有效期：24 小时 */
    private static final long ARCHIVE_TTL_MS = 24L * 60 * 60 * 1000;

    /** 单次打包最多文件数 */
    private static final int MAX_FILES = 20_000;

    /** 单次打包最大源字节数：200GB */
    private static final long MAX_SOURCE_BYTES = 200L * 1024 * 1024 * 1024;

    private final FolderDownloadRedis redis;
    private final FileInfoMapperService fileInfoMapperService;
    private final StorageService storageService;
    private final FolderDownloadMqProducer folderDownloadMqProducer;

    public FolderDownloadTaskServiceImpl(FolderDownloadRedis redis,
                                         FileInfoMapperService fileInfoMapperService,
                                         StorageService storageService,
                                         FolderDownloadMqProducer folderDownloadMqProducer) {
        this.redis = redis;
        this.fileInfoMapperService = fileInfoMapperService;
        this.storageService = storageService;
        this.folderDownloadMqProducer = folderDownloadMqProducer;
    }

    @Override
    public FolderDownloadTaskVO create(Long folderId) {
        Long userId = UserContext.getCurrentUserId();
        FileInfo folder = fileInfoMapperService.findByIdAndUserId(folderId, userId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(folder.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER, "目标不是文件夹");
        }
        String rootPath = normalizeDirectory(folder.getLocalFilePath());
        List<FileInfo> subtree = fileInfoMapperService.getSubTree(rootPath, userId);
        long fileCount = subtree.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsFolder()))
                .count();
        long totalBytes = subtree.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsFolder()))
                .mapToLong(f -> f.getFileSize() == null ? 0 : f.getFileSize())
                .sum();
        if (fileCount > MAX_FILES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件数量超过20000，无法创建打包任务");
        }
        if (totalBytes > MAX_SOURCE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件夹总大小超过200GB");
        }

        long now = System.currentTimeMillis();
        FolderDownloadTask task = new FolderDownloadTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(userId);
        task.setFolderId(folderId);
        task.setFolderName(folder.getFilename());
        task.setStatus(FolderDownloadStatus.PENDING);
        task.setTotalBytes(totalBytes);
        task.setMessage("等待后台打包");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        redis.save(task);
        try {
            folderDownloadMqProducer.sendFolderDownloadTask(task.getTaskId());
        } catch (RuntimeException e) {
            logger.error("文件夹打包任务投递失败: taskId={}, userId={}, folderId={}",
                    task.getTaskId(), userId, folderId, e);
            fail(task.getTaskId(), "MQ任务发送失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建文件夹下载任务失败");
        }
        logger.info("文件夹打包任务创建完成: taskId={}, userId={}, folderId={}, totalBytes={}",
                task.getTaskId(), userId, folderId, totalBytes);
        return toVO(task);
    }

    @Override
    public FolderDownloadTaskVO getOwnedTask(String taskId) {
        return toVO(requireOwned(taskId));
    }

    @Override
    public FolderDownloadTask getDownloadableTask(String taskId) {
        FolderDownloadTask task = requireOwned(taskId);
        if (task.getStatus() != FolderDownloadStatus.SUCCESS || task.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "压缩包尚未生成");
        }
        if (task.getExpiresAt() <= System.currentTimeMillis()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "压缩包已过期");
        }
        return task;
    }

    @Override
    public void cancel(String taskId) {
        FolderDownloadTask snapshot = requireOwned(taskId);
        redis.update(taskId, current -> {
            current.setCancelRequested(true);
            current.setUpdatedAt(System.currentTimeMillis());
            if (current.getStatus() == FolderDownloadStatus.PENDING) {
                current.setStatus(FolderDownloadStatus.CANCELLED);
                current.setMessage("任务已取消");
            }
        });
        if (snapshot.getStatus() == FolderDownloadStatus.SUCCESS && snapshot.getObjectKey() != null) {
            try {
                storageService.deleteFile(snapshot.getObjectKey());
            } catch (RuntimeException e) {
                logger.warn("删除已完成的临时压缩包失败: taskId={}, objectKey={}",
                        taskId, snapshot.getObjectKey(), e);
            }
            redis.update(taskId, current -> {
                current.setStatus(FolderDownloadStatus.CANCELLED);
                current.setObjectKey(null);
                current.setMessage("任务已取消，临时压缩包已删除");
                current.setUpdatedAt(System.currentTimeMillis());
            });
        }
    }

    /**
     * 查询并校验任务归属
     *
     * @param taskId 任务ID
     * @return 任务对象
     */
    private FolderDownloadTask requireOwned(String taskId) {
        FolderDownloadTask task = redis.get(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件夹下载任务不存在");
        }
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return task;
    }

    /**
     * 转任务视图
     *
     * @param task 任务对象
     * @return 任务视图
     */
    private FolderDownloadTaskVO toVO(FolderDownloadTask task) {
        return new FolderDownloadTaskVO(task.getTaskId(), task.getFolderName(), task.getStatus(),
                task.getTotalBytes(), task.getProcessedBytes(), task.getProgress(), task.getMessage(),
                task.getStatus() == FolderDownloadStatus.SUCCESS
                        && task.getExpiresAt() > System.currentTimeMillis(),
                task.getExpiresAt());
    }

    /**
     * 标记任务失败
     *
     * @param taskId  任务ID
     * @param message 失败原因
     */
    private void fail(String taskId, String message) {
        redis.update(taskId, current -> {
            current.setStatus(FolderDownloadStatus.FAILED);
            current.setMessage(message == null ? "打包失败" : message);
            current.setUpdatedAt(System.currentTimeMillis());
        });
    }

    /**
     * 目录路径规范化：统一以 "/" 结尾
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    private String normalizeDirectory(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
