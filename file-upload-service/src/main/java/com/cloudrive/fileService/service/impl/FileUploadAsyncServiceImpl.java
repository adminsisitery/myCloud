package com.cloudrive.fileService.service.impl;

import com.cloudrive.fileService.client.UserSpaceClient;
import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.entity.FileObject;
import com.cloudrive.fileService.mq.MqProducer;
import com.cloudrive.fileService.repositorys.FileObjectMapper;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.FileUploadAsyncService;
import com.cloudrive.fileService.service.StorageService;
import com.cloudrive.fileService.service.StorageServiceFactory;
import com.cloudrive.fileService.service.UpLoadProgressService;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.domain.mq.PgIndexMessage;
import com.cloudrive.pan_common.domain.mq.UsageMessage;
import com.cloudrive.pan_common.domain.user.SpaceUsage;
import com.cloudrive.pan_common.enums.FileObjectStatus;
import com.cloudrive.pan_common.util.FileHashUtil;
import com.cloudrive.pan_common.util.FileTypeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 普通文件异步上传服务实现
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.service.impl.FileUploadAsyncServiceImpl} +
 * {@code FileServiceImpl#uploadFileWithProgressFromPath} 的上传关键路径，
 * 保留了同样的行为：哈希命中当前用户已有文件 -> 秒传；否则先推 OSS 再落台账，
 * 落库失败清理孤儿对象；成功/失败都把预占额度结清（转真实占用或归还）。
 * </p>
 * <p>
 * 上传成功后会向 file-async-service 投递旁路消息：ES 索引、PG 向量（rebuild=true，
 * 消费端从 OSS 重建文本）、操作日志、以及空间使用率超过 90% 时的用量提醒。
 * 消息发送失败不影响上传结果。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Slf4j
@Service
public class FileUploadAsyncServiceImpl implements FileUploadAsyncService {

    private final UpLoadProgressService upLoadProgressService;
    private final UserSpaceClient userSpaceClient;
    private final StorageServiceFactory storageServiceFactory;
    private final FileMapper fileMapper;
    private final FileInfoMapperService fileInfoMapperService;
    private final FileObjectMapper fileObjectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MqProducer mqProducer;

    public FileUploadAsyncServiceImpl(UpLoadProgressService upLoadProgressService,
                                      UserSpaceClient userSpaceClient,
                                      StorageServiceFactory storageServiceFactory,
                                      FileMapper fileMapper,
                                      FileInfoMapperService fileInfoMapperService,
                                      FileObjectMapper fileObjectMapper,
                                      TransactionTemplate transactionTemplate,
                                      MqProducer mqProducer) {
        this.upLoadProgressService = upLoadProgressService;
        this.userSpaceClient = userSpaceClient;
        this.storageServiceFactory = storageServiceFactory;
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileObjectMapper = fileObjectMapper;
        this.transactionTemplate = transactionTemplate;
        this.mqProducer = mqProducer;
    }

    @Async("fileUploadExecutor")
    @Override
    public void asyncUploadFile(String fileType, File tmpFile, String originalFilename, long fileSize,
                                Long parentId, String taskId, Long currentUserId) {
        try {
            // 用户可能在上传途中取消：取消方只负责置状态，归还由仍持有执行权的线程做
            if (upLoadProgressService.isCancelled(taskId)) {
                log.info("上传任务已取消，跳过上传: taskId={}", taskId);
                releaseSpaceOnce(taskId, currentUserId, fileSize);
                return;
            }

            upLoadProgressService.markUploading(taskId);
            log.info("普通上传开始: taskId={}, userId={}, fileSize={}", taskId, currentUserId, fileSize);

            FileInfo uploaded = uploadFile(fileType, tmpFile, originalFilename, fileSize, parentId, taskId, currentUserId);
            publishUploadSideEffects(uploaded);

            // 文件已落库：预占转为真实占用，先结清标记再置成功，避免超时扫描误解
            upLoadProgressService.clearSpaceReserved(taskId);
            upLoadProgressService.completeUploadTask(taskId, true, "文件上传成功");
            log.info("普通上传成功: taskId={}, userId={}", taskId, currentUserId);
        } catch (Exception e) {
            log.error("普通上传失败: taskId={}, userId={}", taskId, currentUserId, e);
            try {
                releaseSpaceOnce(taskId, currentUserId, fileSize);
            } catch (RuntimeException releaseEx) {
                log.error("上传失败后归还预占空间失败，等待超时扫描补偿: taskId={}", taskId, releaseEx);
            }
            if (!upLoadProgressService.isCancelled(taskId)) {
                upLoadProgressService.completeUploadTask(taskId, false, failureMessage(e));
            }
        } finally {
            deleteTempFile(tmpFile, taskId);
        }
    }

    /**
     * 上传核心逻辑：秒传判定 -> 推 OSS（带进度）-> 落台账。
     *
     * @return 落库后的文件记录
     */
    private FileInfo uploadFile(String fileType, File tmpFile, String originalFilename, long fileSize,
                                Long parentId, String taskId, Long currentUserId) throws Exception {
        if (tmpFile == null || !tmpFile.exists() || !tmpFile.isFile()) {
            throw new IllegalStateException("上传临时文件不存在或不是常规文件");
        }

        String sha256Hash = FileHashUtil.calculateSHA256(tmpFile);

        // 秒传只允许复用「当前用户已经拥有过的文件」，不跨用户复用物理对象
        List<FileInfo> sameUserFiles = fileInfoMapperService.findByHashAndUserId(sha256Hash, currentUserId);
        if (!sameUserFiles.isEmpty()) {
            FileInfo reused = handleFastUpload(originalFilename, fileSize, sameUserFiles.get(0), sha256Hash,
                    parentId, taskId, currentUserId);
            // 秒传复用已有物理对象：只补 ES 索引 + 操作日志，不重复重建向量
            publishFastUploadSideEffects(reused);
            return reused;
        }

        String uploadPath = CommonConstants.File.FILE_PATH_PREFIX + currentUserId;
        StorageService storageService = storageServiceFactory.getStorageService();
        String objectKey = storageService.uploadFileWithProgressFromPath(tmpFile, uploadPath, taskId,
                originalFilename, fileSize, fileType);

        String[] nameParts = splitFilename(originalFilename);
        String[] resolved = resolveUploadNameConflict(nameParts[0], nameParts[1], parentId, currentUserId);
        String finalBaseName = resolved[0];
        String finalSuffix = resolved[1];
        String resolvedFilename = joinFilename(finalBaseName, finalSuffix);
        String localFilePath = resolveLocalFilePath(parentId, resolvedFilename, currentUserId);

        try {
            FileInfo saved = transactionTemplate.execute(status -> {
                FileInfo fileInfo = fileMapper.toFileInfoFromPath(finalBaseName, objectKey, fileSize,
                        currentUserId, parentId, sha256Hash, finalSuffix, fileType, resolvedFilename, localFilePath);
                fileInfo.setCategoryId(FileTypeUtil.getCategoryId(finalSuffix));
                FileObject fileObject = fileMapper.toFileObject(fileInfo, FileObjectStatus.AVAILABLE);
                fileInfoMapperService.save(fileInfo);
                fileObjectMapper.createFileObject(fileObject);
                return fileInfo;
            });
            if (saved == null || saved.getId() == null) {
                throw new IllegalStateException("文件信息保存失败");
            }
            // PG 消息带 contentHash 与 rebuild=true，消费端从 OSS 重建文本，避免异步线程解析大文件
            mqProducer.sendPgInsert(new PgIndexMessage(saved.getId(), currentUserId,
                    null, sha256Hash, true));
            return saved;
        } catch (Exception e) {
            log.error("文件信息落库失败，清理孤儿 OSS 对象: objectKey={}, taskId={}", objectKey, taskId, e);
            try {
                storageService.deleteFile(objectKey);
            } catch (Exception cleanupEx) {
                log.warn("清理孤儿 OSS 对象失败: objectKey={}", objectKey, cleanupEx);
            }
            throw e;
        }
    }

    /**
     * 秒传落库：复用已有物理对象的存储路径，只新增一条文件记录（不重复占 OSS 空间）。
     *
     * @return 新生成的文件记录
     */
    private FileInfo handleFastUpload(String originalFilename, long fileSize, FileInfo existingFile,
                                      String sha256Hash, Long parentId, String taskId, Long currentUserId) {
        String[] nameParts = splitFilename(originalFilename);
        String[] resolved = resolveUploadNameConflict(nameParts[0], nameParts[1], parentId, currentUserId);
        String finalBaseName = resolved[0];
        String finalSuffix = resolved[1];
        String resolvedFilename = joinFilename(finalBaseName, finalSuffix);

        FileInfo newFileInfo = fileMapper.toFileInfoForFastUpload(resolvedFilename, finalBaseName,
                existingFile, currentUserId, parentId, sha256Hash);
        // toFileInfoForFastUpload 未映射 fileSuffix/localFilePath/categoryId，必须在此补齐
        newFileInfo.setFileSuffix(finalSuffix);
        newFileInfo.setCategoryId(FileTypeUtil.getCategoryId(finalSuffix));
        newFileInfo.setLocalFilePath(resolveLocalFilePath(parentId, resolvedFilename, currentUserId));
        fileInfoMapperService.save(newFileInfo);

        if (taskId != null) {
            // 秒传没有真实字节传输，直接把进度推到 100%
            upLoadProgressService.updateProgress(taskId, fileSize, fileSize);
        }
        log.info("命中秒传，复用已有文件: taskId={}, userId={}, fileId={}", taskId, currentUserId, newFileInfo.getId());
        return newFileInfo;
    }

    /**
     * 上传成功后的旁路消息：ES 索引 + 操作日志 + 用量提醒。
     */
    private void publishUploadSideEffects(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getId() == null) {
            return;
        }
        String displayName = buildDisplayName(fileInfo);
        mqProducer.sendIndexInsert(new FileIndexMessage(fileInfo.getId(), displayName, null,
                fileInfo.getUserId(), fileInfo.getFileType()));
        sendOperationLog(fileInfo, displayName);
        notifyUsageIfNeeded(fileInfo.getUserId());
    }

    /**
     * 秒传成功后的旁路消息：ES 索引 + 操作日志（物理对象未变，无需重建 PG 向量）。
     */
    private void publishFastUploadSideEffects(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getId() == null) {
            return;
        }
        String displayName = buildDisplayName(fileInfo);
        mqProducer.sendIndexInsert(new FileIndexMessage(fileInfo.getId(), displayName, null,
                fileInfo.getUserId(), fileInfo.getFileType()));
        sendOperationLog(fileInfo, displayName);
    }

    private void sendOperationLog(FileInfo fileInfo, String displayName) {
        mqProducer.sendLog(new OperationLogMessage(
                fileInfo.getUserId(),
                "UPLOAD",
                fileInfo.getId(),
                displayName,
                fileInfo.getFileSize(),
                null,
                null,
                UUID.randomUUID().toString(),
                LocalDateTime.now()));
    }

    /**
     * 空间使用率超过 90% 时投递用量提醒（消费端走 WebSocket + 邮件）。
     */
    private void notifyUsageIfNeeded(Long userId) {
        if (userId == null) {
            return;
        }
        SpaceUsage usage = userSpaceClient.getSpaceUsage(userId);
        if (usage == null || usage.totalSpace() <= 0) {
            return;
        }
        long percent = usage.usedSpace() * 100 / usage.totalSpace();
        if (percent >= 90) {
            mqProducer.sendUsage(new UsageMessage(userId, usage.usedSpace(), usage.totalSpace(),
                    "可用空间剩余不足10%，已用" + percent + "%"));
        }
    }

    private String buildDisplayName(FileInfo fileInfo) {
        String suffix = fileInfo.getFileSuffix();
        return (suffix == null || suffix.isEmpty())
                ? fileInfo.getFilename()
                : fileInfo.getFilename() + "." + suffix;
    }

    /**
     * 拆文件名：无后缀时 baseName 为原名、suffix 为空串。
     */
    private String[] splitFilename(String originalFilename) {
        int dotIdx = originalFilename == null ? -1 : originalFilename.lastIndexOf(".");
        String baseName = dotIdx > 0 ? originalFilename.substring(0, dotIdx) : originalFilename;
        String suffix = dotIdx > 0 ? originalFilename.substring(dotIdx + 1) : "";
        return new String[]{baseName, suffix};
    }

    private String joinFilename(String baseName, String suffix) {
        return (suffix == null || suffix.isEmpty()) ? baseName : baseName + "." + suffix;
    }

    /**
     * 同级目录重名时自动加序号：test.txt -> test(1).txt -> ...
     */
    private String[] resolveUploadNameConflict(String baseName, String suffix, Long parentId, Long currentUserId) {
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        String resolvedBase = baseName;
        String resolvedSuffix = suffix;

        if (hasNameConflict(resolvedBase, resolvedSuffix, siblings)) {
            int counter = 1;
            while (hasNameConflict(baseName + "(" + counter + ")", resolvedSuffix, siblings) && counter < 1000) {
                counter++;
            }
            resolvedBase = baseName + "(" + counter + ")";
            log.info("上传文件同名冲突，自动重命名：{}.{} -> {}.{}", baseName, suffix, resolvedBase, resolvedSuffix);
        }
        return new String[]{resolvedBase, resolvedSuffix};
    }

    private boolean hasNameConflict(String name, String suffix, List<FileInfo> siblings) {
        if (siblings == null) {
            return false;
        }
        String fullName = (suffix != null && !suffix.isEmpty()) ? name + "." + suffix : name;
        for (FileInfo file : siblings) {
            String siblingName = (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty())
                    ? file.getFilename() + "." + file.getFileSuffix()
                    : file.getFilename();
            if (fullName.equalsIgnoreCase(siblingName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算虚拟目录路径：父目录本地路径 + 解析后的文件名；根目录返回空串。
     */
    private String resolveLocalFilePath(Long parentId, String resolvedFilename, Long currentUserId) {
        if (parentId == null) {
            return "";
        }
        FileInfo parent = fileInfoMapperService.findByIdAndUserId(parentId, currentUserId);
        return (parent != null && parent.getLocalFilePath() != null)
                ? parent.getLocalFilePath() + resolvedFilename : "";
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

    private String failureMessage(Exception e) {
        String detail = e.getMessage();
        return (detail == null || detail.isBlank()) ? "文件上传失败" : "文件上传失败: " + detail;
    }
}
