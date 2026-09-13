package com.ff.mycloud.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.aliyun.oss.model.PartETag;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.FileObjectStatus;
import com.ff.mycloud.mapper.ChunkUploadMapper;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.*;
import com.ff.mycloud.model.vo.ChunkCompleteVO;
import com.ff.mycloud.model.vo.ChunkPartVO;
import com.ff.mycloud.model.vo.ChunkProgressVO;
import com.ff.mycloud.model.vo.ChunkVO;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.redisson.api.RLock;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private final UserCenterMapperService userCenterMapperService;
    private final StorageServiceFactory storageServiceFactory;
    private final ChunkUploadRedis chunkUploadRedis;
    private final ChunkUploadMapper chunkUploadMapper;
    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;
    private final FileObjectMapper fileObjectMapper;

    public ChunkUploadServiceImpl(UserCenterMapperService userCenterMapperService,
                                   StorageServiceFactory storageServiceFactory,
                                   ChunkUploadRedis chunkUploadRedis,
                                   ChunkUploadMapper chunkUploadMapper,
                                   FileInfoMapperService fileInfoMapperService,
                                   FileMapper fileMapper,
                                   FileObjectMapper fileObjectMapper) {
        this.userCenterMapperService = userCenterMapperService;
        this.storageServiceFactory = storageServiceFactory;
        this.chunkUploadRedis = chunkUploadRedis;
        this.chunkUploadMapper = chunkUploadMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
        this.fileObjectMapper = fileObjectMapper;
    }

    @Override
    public Result<ChunkVO> initUpload(ChunkFile chunkFile) {
        Long currentUserId = UserContext.getCurrentUserId();
        User currentUser = UserContext.getCurrentUser();

        String validationError = validateInitRequest(chunkFile, currentUserId);
        if (validationError != null) {
            return Result.error(validationError);
        }

        // 1. 原子预占空间
        if (!userCenterMapperService.reserveSpace(currentUserId, chunkFile.getFileSize())) {
            log.warn("分片任务空间不足: userId={}, fileSize={}", currentUserId, chunkFile.getFileSize());
            return Result.error("用户可用空间不足");
        }

        // 2. 生成OSS objectKey：user_{userId}/{uuid}.{suffix}
        String uploadPath = CommonConstants.File.FILE_PATH_PREFIX + currentUser.getId();
        String suffix = "";
        String fileName = chunkFile.getFileName();
        if (fileName != null && fileName.contains(".")) {
            suffix = fileName.substring(fileName.lastIndexOf("."));
        }
        String objectKey = uploadPath + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;

        // 3. 初始化OSS分片上传
        String ossUploadId = null;
        try {
            ossUploadId = storageServiceFactory.getStorageService()
                    .initiateMultipartUpload(objectKey, chunkFile.getFileType());

            // 4. 生成自定义uploadId，组装ChunkTask写入Redis
            String uploadId = UUID.randomUUID().toString().replace("-", "");
            ChunkTask chunkTask = chunkUploadMapper.toChunkTask(chunkFile, uploadId);
            chunkTask.setOssUploadId(ossUploadId);
            chunkTask.setObjectKey(objectKey);
            chunkTask.setUserId(currentUserId);
            chunkTask.setSpacePreAllocated(true);
            chunkTask.setStorageCompleted(false);
            chunkTask.setCreatedAt(System.currentTimeMillis());
            chunkTask.setLastActivityTime(System.currentTimeMillis());
            chunkUploadRedis.saveTask(chunkTask);
            log.info("分片任务创建成功: userId={}, uploadId={}, fileSize={}, totalParts={}",
                    currentUserId, uploadId, chunkFile.getFileSize(), chunkFile.getTotalParts());
            return Result.success(new ChunkVO(uploadId, ossUploadId));
        } catch (RuntimeException e) {
            log.error("分片任务初始化失败: userId={}, objectKey={}", currentUserId, objectKey, e);
            if (ossUploadId != null) {
                try {
                    log.info("取消初始化失败的OSS上传: userId={}, objectKey={}", currentUserId, objectKey);
                    storageServiceFactory.getStorageService().cancelUpload(ossUploadId, objectKey);
                } catch (Exception ignored) {
                    log.warn("清理初始化残片失败: userId={}, objectKey={}", currentUserId, objectKey, ignored);
                }
            }
            log.info("释放分片初始化预占空间: userId={}, fileSize={}", currentUserId, chunkFile.getFileSize());
            userCenterMapperService.releaseSpace(currentUserId, chunkFile.getFileSize());
            throw e;
        }
    }

    @Override
    public Result<ChunkPartVO> uploadPart(String uploadId, Integer partNumber, MultipartFile chunk) {
        // 1. 从 Redis 取出上传任务
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            log.warn("分片上传中：分片任务不存在：uploadId：{},PartNumber:{}", uploadId, partNumber);
            return Result.error("uploadId不存在或已过期");
        }

        if (!task.getUserId().equals(UserContext.getCurrentUserId())) {
            log.warn("分片上传中：文件无权限：uploadId；{},partNumber:{}", uploadId, partNumber);
            return Result.error("无权访问此上传任务");
        }
        if (partNumber == null || partNumber < 1 || partNumber > task.getTotalParts()) {
            log.warn("分片上传中：分片序号超出范围uploadId；{},partNumber:{}", uploadId, partNumber);
            return Result.error("分片序号超出范围");
        }
        if (chunk == null || chunk.isEmpty()) {
            log.warn("分片上传中：分片内容不能为空：uploadId：{},partNumber:{}", uploadId, partNumber);
            return Result.error("分片内容不能为空");
        }

        RLock lock = chunkUploadRedis.getPartLock(uploadId, partNumber);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 2, TimeUnit.MINUTES);
            if (!locked) {
                log.debug("分片已经在上传中：uploadId:{},partNumber:{}", uploadId, partNumber);
                return Result.error("分片正在上传，请稍后重试");
            }
            // 锁内二次判断，同一分片重复请求直接返回成功（幂等）。
            if (chunkUploadRedis.isPartUploaded(uploadId, partNumber)) {
                log.debug("分片已经上传过了：uploadId:{},partNumber:{}", uploadId, partNumber);
                return Result.success(completedPart(uploadId, partNumber));
            }
            long expectedMax = partNumber.equals(task.getTotalParts())
                    ? task.getFileSize() - (long) task.getChunkSize() * (task.getTotalParts() - 1)
                    : task.getChunkSize();
            if (chunk.getSize() > task.getChunkSize() || (partNumber.equals(task.getTotalParts()) && chunk.getSize() > expectedMax)) {
                log.warn("分片大小与初始化参数不一致:UploadId:{},partNumber:{}", uploadId, partNumber);
                return Result.error("分片大小与初始化参数不一致");
            }

            StorageService storageService = storageServiceFactory.getStorageService();
            String eTag = storageService.uploadPart(
                    task.getObjectKey(),
                    task.getOssUploadId(),
                    partNumber,
                    chunk.getInputStream(),
                    chunk.getSize());

            // 4. 记录分片 ETag + 标记位图 + 刷新活动时间
            chunkUploadRedis.savePartETag(uploadId, partNumber, eTag);
            chunkUploadRedis.markPartDone(uploadId, partNumber);
            task.setLastActivityTime(System.currentTimeMillis());
            chunkUploadRedis.saveTask(task);
            log.debug("分片上传成功：uploadId：{}，chunkNumber：{}", uploadId, partNumber);
            return Result.success(completedPart(uploadId, partNumber));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("分片上传失败: uploadId={}, partNumber={}", uploadId, partNumber, e);
            return Result.error("分片上传失败: " + e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ChunkCompleteVO> completeChunk(String uploadId) {
        RLock lock = chunkUploadRedis.getTaskLock(uploadId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 2, TimeUnit.MINUTES);
            if (!locked) return Result.error("任务正在合并，请稍后查询");
            return doCompleteChunk(uploadId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("分片合并请求中断: uploadId={}", uploadId);
            return Result.error("合并请求被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    protected Result<ChunkCompleteVO> doCompleteChunk(String uploadId) {
        // 1. 取出上传任务
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) {
            return Result.error("无权访问此上传任务");
        }

        // 2. 从位图找出缺失的分片号
        List<Integer> missing = chunkUploadRedis.getMissingParts(uploadId, task.getTotalParts());
        if (!missing.isEmpty()) {
            log.warn("合并缺少分片: uploadId={}, missingCount={}, totalParts={}", uploadId, missing.size(), task.getTotalParts());
            return Result.error("缺失分片号: " + missing
                    + "，已完成: " + (task.getTotalParts() - missing.size()) + " / " + task.getTotalParts());
        }

        // 3. 获取所有分片 ETag（按 partNumber 升序）
        List<PartETag> partETags = chunkUploadRedis.getPartETags(uploadId);

        // 4. 调用 OSS 合并分片
        StorageService storageService = storageServiceFactory.getStorageService();
        String objectKey = task.getObjectKey();
        if (!Boolean.TRUE.equals(task.getStorageCompleted())) {
            objectKey = storageService.completeChunk(
                    task.getObjectKey(),
                    task.getOssUploadId(),
                    partETags
            );
            task.setStorageCompleted(true);
            task.setLastActivityTime(System.currentTimeMillis());
            chunkUploadRedis.saveTask(task);
        }

        // 5. 解析文件名和后缀
        String originalFilename = task.getFileName();
        String suffix = "";
        String nameWithoutSuffix = originalFilename;
        if (originalFilename != null && originalFilename.contains(".")) {
            int dotIdx = originalFilename.lastIndexOf(".");
            suffix = originalFilename.substring(dotIdx + 1);
            nameWithoutSuffix = originalFilename.substring(0, dotIdx);
        }

        // 6. 计算 localFilePath
        String localFilePath = "";
        if (task.getParentId() != null && task.getParentId() != 0) {
            FileInfo parent = fileInfoMapperService.findByIdAndUserId(task.getParentId(), task.getUserId());
            if (parent != null) {
                localFilePath = parent.getLocalFilePath() + originalFilename;
            }
        }

        // 7. 保存 FileInfo 和 FileObject
        FileInfo fileInfo = fileMapper.toFileInfoFromPath(
                nameWithoutSuffix, objectKey, task.getFileSize(),
                task.getUserId(), task.getParentId(), task.getSha256Hash(),
                suffix, task.getFileType(), originalFilename, localFilePath);
        fileInfo.setCategoryId(FileTypeUtil.getCategoryId(suffix));
        fileInfoMapperService.save(fileInfo);

        FileObject fileObject = fileMapper.toFileObject(fileInfo, FileObjectStatus.AVAILABLE);
        fileObjectMapper.createFileObject(fileObject);

        // 8. 清理 Redis
        chunkUploadRedis.deleteTask(uploadId);

        log.info("分片合并完成，等待事务提交: userId={}, uploadId={}, fileId={}, fileSize={}",
                task.getUserId(), uploadId, fileInfo.getId(), task.getFileSize());
        // 9. 返回文件信息
        return Result.success(new ChunkCompleteVO(
                fileInfo.getId(), originalFilename,
                task.getFileSize(), objectKey, suffix));
    }

    @Override
    public Result<ChunkProgressVO> getMissingList(String uploadId) {
        // 1. 取出上传任务
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) {
            return Result.error("无权访问此上传任务");
        }

        // 2. 从位图找出缺失和已完成的分片号
        List<Integer> missing = chunkUploadRedis.getMissingParts(uploadId, task.getTotalParts());
        List<Integer> completeParts = chunkUploadRedis.getCompleteParts(uploadId, task.getTotalParts());
        Double progressPercent = task.getTotalParts() > 0
                ? (double) completeParts.size() / task.getTotalParts()
                : 0.0;

        ChunkProgressVO chunkProgressVO = new ChunkProgressVO(
                uploadId,task.getFileName(),task.getFileSize(),task.getTotalParts(),completeParts,missing,progressPercent
        );
        return Result.success(chunkProgressVO);
    }

    @Override
    public Result<String> cancelUpload(String uploadId) {
        RLock lock = chunkUploadRedis.getTaskLock(uploadId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 1, TimeUnit.MINUTES);
            if (!locked) return Result.error("任务正在处理，请稍后重试");
            return doCancelUpload(uploadId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("分片取消请求中断: uploadId={}", uploadId);
            return Result.error("取消请求被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private Result<String> doCancelUpload(String uploadId) {
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }
        if (!task.getUserId().equals(UserContext.getCurrentUserId())) {
            return Result.error("无权访问此上传任务");
        }

        // 1. 取消 OSS 分片上传
        StorageService storageService = storageServiceFactory.getStorageService();
        if (Boolean.TRUE.equals(task.getStorageCompleted())) {
            storageService.deleteFile(task.getObjectKey());
        } else {
            storageService.cancelUpload(task.getOssUploadId(), task.getObjectKey());
        }

        // 2. 归还预占空间
        userCenterMapperService.releaseSpace(task.getUserId(), task.getFileSize());

        // 3. 清理 Redis
        chunkUploadRedis.deleteTask(uploadId);

        log.info("分片任务取消完成: userId={}, uploadId={}, releasedBytes={}", task.getUserId(), uploadId, task.getFileSize());
        return Result.success("已取消");
    }

    private ChunkPartVO completedPart(String uploadId, Integer partNumber) {
        ChunkPartVO vo = new ChunkPartVO();
        vo.setUploadId(uploadId);
        vo.setPartNumber(partNumber);
        vo.setCompleted(true);
        return vo;
    }

    private String validateInitRequest(ChunkFile request, Long userId) {
        if (request == null || request.getFileName() == null || request.getFileName().isBlank()) return "文件名不能为空";
        if (request.getFileSize() == null || request.getFileSize() <= 0) return "文件大小必须大于0";
        if (request.getTotalParts() == null || request.getTotalParts() < 1 || request.getTotalParts() > 10000) return "分片数量必须在1到10000之间";
        if (request.getChunkSize() == null || request.getChunkSize() < 100 * 1024) return "分片大小不能小于100KB";
        long expectedParts = (request.getFileSize() + request.getChunkSize() - 1) / request.getChunkSize();
        if (expectedParts != request.getTotalParts()) return "分片数量与文件大小不匹配";
        if (request.getParentId() != null && request.getParentId() != 0
                && !fileInfoMapperService.existsParentId(request.getParentId(), userId)) return "目标文件夹不存在或无权访问";
        return null;
    }
}
