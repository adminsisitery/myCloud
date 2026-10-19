package com.cloudrive.fileService.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import com.cloudrive.fileService.config.properties.OssProProperties;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.enums.FileObjectStatus;
import com.cloudrive.fileService.mapper.FileMapper;
import com.cloudrive.fileService.model.dto.DirectUploadInitDTO;
import com.cloudrive.fileService.model.entity.DirectUploadTask;
import com.cloudrive.fileService.model.entity.FileInfo;
import com.cloudrive.fileService.model.entity.FileObject;
import com.cloudrive.fileService.model.vo.ChunkCompleteVO;
import com.cloudrive.fileService.model.vo.DirectUploadInitVO;
import com.cloudrive.fileService.redis.DirectUploadRedis;
import com.cloudrive.fileService.repositorys.FileObjectMapper;
import com.cloudrive.fileService.service.DirectUploadService;
import com.cloudrive.fileService.service.FileInfoMapperService;
import com.cloudrive.fileService.service.StorageService;
import com.cloudrive.fileService.service.StorageServiceFactory;
import com.cloudrive.fileService.client.UserSpaceClient;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DirectUploadServiceImpl implements DirectUploadService {
    private final OssProProperties oss;
    private final DirectUploadRedis redis;
    private final UserSpaceClient userSpaceClient;
    private final FileInfoMapperService fileInfoService;
    private final StorageServiceFactory storageFactory;
    private final FileMapper fileMapper;
    private final FileObjectMapper fileObjectMapper;
    private final TransactionTemplate transactionTemplate;

    public DirectUploadServiceImpl(OssProProperties oss, DirectUploadRedis redis,
                                   UserSpaceClient userSpaceClient,
                                   FileInfoMapperService fileInfoService,
                                   StorageServiceFactory storageFactory,
                                   FileMapper fileMapper, FileObjectMapper fileObjectMapper,
                                   TransactionTemplate transactionTemplate) {
        this.oss = oss;
        this.redis = redis;
        this.userSpaceClient = userSpaceClient;
        this.fileInfoService = fileInfoService;
        this.storageFactory = storageFactory;
        this.fileMapper = fileMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public DirectUploadInitVO init(DirectUploadInitDTO request) {
        Long userId = UserContext.getCurrentUserId();
        validateParent(request.getParentId(), userId);
        validateStsConfiguration();
        if (!userSpaceClient.reserveSpace(userId, request.getFileSize())) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "用户可用空间不足");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String suffix = suffixWithDot(request.getFileName());
        String objectKey = "user_" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
        try {
            AssumeRoleResponse.Credentials credentials = assumeRole(userId, taskId, objectKey);
            long now = System.currentTimeMillis();
            DirectUploadTask task = new DirectUploadTask();
            task.setTaskId(taskId);
            task.setUserId(userId);
            task.setObjectKey(objectKey);
            task.setFileName(request.getFileName());
            task.setFileSize(request.getFileSize());
            task.setFileType(normalizeContentType(request.getFileType()));
            task.setParentId(request.getParentId());
            task.setSha256Hash(request.getSha256Hash().toLowerCase(Locale.ROOT));
            task.setCreatedAt(now);
            task.setExpiresAt(now + (oss.getSts().getDurationSeconds() + oss.getSts().getConfirmGraceSeconds()) * 1000);
            redis.save(task);
            log.info("直传任务创建成功: userId={}, taskId={}, fileSize={}", userId, taskId, task.getFileSize());

            Map<String, String> requiredHeaders = new LinkedHashMap<>();
            requiredHeaders.put("Content-Type", task.getFileType());
            requiredHeaders.put("x-oss-meta-sha256", task.getSha256Hash());
            return new DirectUploadInitVO(taskId, credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(), credentials.getSecurityToken(), credentials.getExpiration(),
                    normalizeEndpoint(oss.getEndpoint()), oss.getBucketName(), objectKey,
                    oss.getSts().getMaxParallelParts(), requiredHeaders);
        } catch (RuntimeException e) {
            log.error("直传初始化失败，开始释放预占空间: userId={}, taskId={}", userId, taskId, e);
            userSpaceClient.releaseSpace(userId, request.getFileSize());
            redis.delete(taskId);
            throw e;
        }
    }

    @Override
    public ChunkCompleteVO complete(String taskId) {
        RLock lock = redis.getLock(taskId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 2, TimeUnit.MINUTES);
            if (!locked) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "直传任务正在确认");
            DirectUploadTask task = requireOwnedTask(taskId);
            if (task.getExpiresAt() < System.currentTimeMillis()) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "直传任务已过期");
            }

            StorageService storage = storageFactory.getStorageService();
            ObjectMetadata metadata = storage.getObjectMetadata(task.getObjectKey());
            if (metadata.getContentLength() != task.getFileSize()) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "OSS对象大小与初始化参数不一致");
            }
            String objectHash = findMetadataIgnoreCase(metadata.getUserMetadata(), "sha256");
            if (objectHash == null || !objectHash.equalsIgnoreCase(task.getSha256Hash())) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED,
                        "缺少或不匹配的x-oss-meta-sha256，请按初始化响应中的requiredHeaders上传");
            }

            FileInfo fileInfo = transactionTemplate.execute(status -> persistFile(task));
            if (fileInfo == null) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "保存文件元数据失败");
            redis.delete(taskId);
            String suffix = suffixWithoutDot(task.getFileName());
            log.info("直传确认完成: userId={}, taskId={}, fileId={}, fileSize={}", task.getUserId(), taskId, fileInfo.getId(), task.getFileSize());
            return new ChunkCompleteVO(fileInfo.getId(), task.getFileName(), task.getFileSize(), task.getObjectKey(), suffix);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "直传确认被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    public void cancel(String taskId) {
        RLock lock = redis.getLock(taskId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 1, TimeUnit.MINUTES);
            if (!locked) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "直传任务正在处理");
            cleanup(requireOwnedTask(taskId));
            log.info("直传任务取消完成: taskId={}", taskId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "取消直传被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (String taskId : redis.scanTaskIds()) {
            DirectUploadTask task = redis.get(taskId);
            if (task != null && task.getExpiresAt() < now) {
                RLock lock = redis.getLock(taskId);
                if (lock.tryLock()) {
                    try {
                        task = redis.get(taskId);
                        if (task != null && task.getExpiresAt() < now) {
                            cleanup(task);
                            log.info("直传超时任务清理完成: taskId={}, userId={}", taskId, task.getUserId());
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }

    private FileInfo persistFile(DirectUploadTask task) {
        String suffix = suffixWithoutDot(task.getFileName());
        String baseName = suffix.isEmpty() ? task.getFileName()
                : task.getFileName().substring(0, task.getFileName().length() - suffix.length() - 1);
        String localPath = "";
        if (task.getParentId() != null && task.getParentId() != 0) {
            FileInfo parent = fileInfoService.findByIdAndUserId(task.getParentId(), task.getUserId());
            if (parent == null || !Boolean.TRUE.equals(parent.getIsFolder())) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "目标文件夹不存在");
            }
            localPath = parent.getLocalFilePath() + task.getFileName();
        }
        FileInfo file = fileMapper.toFileInfoFromPath(baseName, task.getObjectKey(), task.getFileSize(),
                task.getUserId(), task.getParentId(), task.getSha256Hash(), suffix, task.getFileType(),
                task.getFileName(), localPath);
        file.setCategoryId(FileTypeUtil.getCategoryId(suffix));
        if (!fileInfoService.save(file)) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "保存文件记录失败");
        FileObject object = fileMapper.toFileObject(file, FileObjectStatus.AVAILABLE);
        fileObjectMapper.createFileObject(object);
        return file;
    }

    private AssumeRoleResponse.Credentials assumeRole(Long userId, String taskId, String objectKey) {
        DefaultProfile profile = DefaultProfile.getProfile(oss.getSts().getRegionId(),
                oss.getAccessKeyId(), oss.getAccessKeySecret());
        DefaultAcsClient client = new DefaultAcsClient(profile);
        try {
            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setRoleArn(oss.getSts().getRoleArn());
            request.setRoleSessionName(oss.getSts().getRoleSessionName() + "-" + userId + "-" + taskId.substring(0, 8));
            request.setDurationSeconds(oss.getSts().getDurationSeconds());
            request.setPolicy(buildPolicy(objectKey));
            return client.getAcsResponse(request).getCredentials();
        } catch (Exception e) {
            log.error("获取STS临时凭证失败: userId={}, taskId={}", userId, taskId, e);
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "获取STS临时凭证失败: " + e.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private String buildPolicy(String objectKey) {
        return "{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[" +
                "\"oss:PutObject\",\"oss:AbortMultipartUpload\",\"oss:ListParts\"]," +
                "\"Resource\":[\"acs:oss:*:*:" + oss.getBucketName() + "/" + objectKey + "\"]}]}";
    }

    private DirectUploadTask requireOwnedTask(String taskId) {
        DirectUploadTask task = redis.get(taskId);
        if (task == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "直传任务不存在或已完成");
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) throw new BusinessException(ErrorCode.NO_PERMISSION);
        return task;
    }

    private void cleanup(DirectUploadTask task) {
        try {
            storageFactory.getStorageService().deleteFile(task.getObjectKey());
        } finally {
            userSpaceClient.releaseSpace(task.getUserId(), task.getFileSize());
            redis.delete(task.getTaskId());
        }
    }

    private void validateParent(Long parentId, Long userId) {
        if (parentId != null && parentId != 0 && !fileInfoService.existsParentId(parentId, userId)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "目标文件夹不存在或无权访问");
        }
    }

    private void validateStsConfiguration() {
        if (!Boolean.TRUE.equals(oss.getEnabled()) || oss.getSts().getRoleArn() == null
                || oss.getSts().getRoleArn().isBlank()) {
            throw new BusinessException(ErrorCode.OSS_DISABLED, "未配置oss.sts.role-arn，STS直传不可用");
        }
        if (oss.getSts().getDurationSeconds() < 900 || oss.getSts().getDurationSeconds() > 3600) {
            throw new BusinessException(ErrorCode.OSS_UPLOAD_FAILED, "STS凭证有效期必须在900到3600秒之间");
        }
    }

    private String suffixWithDot(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) return "";
        String suffix = filename.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
        return suffix.length() > 20 ? "" : suffix;
    }

    private String suffixWithoutDot(String filename) {
        String suffix = suffixWithDot(filename);
        return suffix.isEmpty() ? "" : suffix.substring(1);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.startsWith("http://") || endpoint.startsWith("https://") ? endpoint : "https://" + endpoint;
    }

    private String findMetadataIgnoreCase(Map<String, String> metadata, String key) {
        if (metadata == null) return null;
        return metadata.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
