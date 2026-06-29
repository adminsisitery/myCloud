package com.ff.mycloud.service.impl;

import com.aliyun.oss.model.PartETag;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.result.Result;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
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

        // 1. 原子预占空间
        if (!userCenterMapperService.reserveSpace(currentUserId, chunkFile.getFileSize())) {
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
        String ossUploadId = storageServiceFactory.getStorageService()
                .initiateMultipartUpload(objectKey);

        // 4. 生成自定义uploadId，组装ChunkTask写入Redis
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        ChunkTask chunkTask = chunkUploadMapper.toChunkTask(chunkFile, uploadId);
        chunkTask.setOssUploadId(ossUploadId);
        chunkTask.setObjectKey(objectKey);
        chunkTask.setUserId(currentUserId);
        chunkTask.setSpacePreAllocated(true);
        chunkTask.setCreatedAt(System.currentTimeMillis());
        chunkUploadRedis.saveTask(chunkTask);

        return Result.success(new ChunkVO(uploadId, ossUploadId));
    }

    @Override
    public Result<ChunkPartVO> uploadPart(String uploadId, Integer partNumber, MultipartFile chunk) {
        // 1. 从 Redis 取出上传任务
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }

        // 2. 幂等判断：分片已完成则跳过
        if (chunkUploadRedis.isPartUploaded(uploadId, partNumber)) {
            return Result.error("分片" + partNumber + "已上传，请勿重复上传");
        }

        // 3. 上传分片到 OSS
        StorageService storageService = storageServiceFactory.getStorageService();
        try {
            String eTag = storageService.uploadPart(
                    task.getObjectKey(),
                    task.getOssUploadId(),
                    partNumber,
                    chunk.getInputStream(),
                    chunk.getSize());

            // 4. 记录分片 ETag + 标记位图
            chunkUploadRedis.savePartETag(uploadId, partNumber, eTag);
            chunkUploadRedis.markPartDone(uploadId, partNumber);

            ChunkPartVO vo = new ChunkPartVO();
            vo.setUploadId(uploadId);
            vo.setPartNumber(partNumber);
            vo.setCompleted(true);
            return Result.success(vo);
        } catch (IOException e) {
            return Result.error("分片上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result<ChunkCompleteVO> completeChunk(String uploadId) {
        // 1. 取出上传任务
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }

        // 2. 从位图找出缺失的分片号
        List<Integer> missing = chunkUploadRedis.getMissingParts(uploadId, task.getTotalParts());
        if (!missing.isEmpty()) {
            return Result.error("缺失分片号: " + missing
                    + "，已完成: " + (task.getTotalParts() - missing.size()) + " / " + task.getTotalParts());
        }

        // 3. 获取所有分片 ETag（按 partNumber 升序）
        List<PartETag> partETags = chunkUploadRedis.getPartETags(uploadId);

        // 4. 调用 OSS 合并分片
        StorageService storageService = storageServiceFactory.getStorageService();
        String objectKey = storageService.completeChunk(
                task.getObjectKey(),
                task.getOssUploadId(),
                partETags
        );

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
            FileInfo parent = fileInfoMapperService.getById(task.getParentId());
            if (parent != null) {
                localFilePath = parent.getLocalFilePath() + originalFilename;
            }
        }

        // 7. 保存 FileInfo 和 FileObject
        FileInfo fileInfo = fileMapper.toFileInfoFromPath(
                nameWithoutSuffix, objectKey, task.getFileSize(),
                task.getUserId(), task.getParentId(), task.getSha256Hash(),
                suffix, task.getFileType(), originalFilename, localFilePath);
        fileInfoMapperService.save(fileInfo);

        FileObject fileObject = fileMapper.toFileObject(fileInfo, FileObjectStatus.AVAILABLE);
        fileObjectMapper.createFileObject(fileObject);

        // 8. 清理 Redis
        chunkUploadRedis.deleteTask(uploadId);

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
        ChunkTask task = chunkUploadRedis.getTask(uploadId);
        if (task == null) {
            return Result.error("uploadId不存在或已过期");
        }

        // 1. 取消 OSS 分片上传
        StorageService storageService = storageServiceFactory.getStorageService();
        storageService.cancelUpload(task.getOssUploadId(), task.getObjectKey());

        // 2. 归还预占空间
        userCenterMapperService.releaseSpace(task.getUserId(), task.getFileSize());

        // 3. 清理 Redis
        chunkUploadRedis.deleteTask(uploadId);

        return Result.success("已取消");
    }
}
