package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.ChunkUploadMapper;
import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.entity.ChunkTask;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.ChunkVO;
import com.ff.mycloud.redis.ChunkUploadRedis;
import com.ff.mycloud.service.ChunkUploadService;
import com.ff.mycloud.service.StorageServiceFactory;
import com.ff.mycloud.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.UUID;

@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private final UserCenterMapperService userCenterMapperService;
    private final StorageServiceFactory storageServiceFactory;
    private final ChunkUploadRedis chunkUploadRedis;
    private final ChunkUploadMapper chunkUploadMapper;

    public ChunkUploadServiceImpl(UserCenterMapperService userCenterMapperService,
                                   StorageServiceFactory storageServiceFactory,
                                   ChunkUploadRedis chunkUploadRedis,
                                   ChunkUploadMapper chunkUploadMapper) {
        this.userCenterMapperService = userCenterMapperService;
        this.storageServiceFactory = storageServiceFactory;
        this.chunkUploadRedis = chunkUploadRedis;
        this.chunkUploadMapper = chunkUploadMapper;
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
        chunkTask.setCompletedParts(new HashSet<>());
        chunkTask.setPartEtags(new java.util.HashMap<>());
        chunkTask.setSpacePreAllocated(true);
        chunkTask.setCreatedAt(System.currentTimeMillis());
        chunkUploadRedis.saveTask(chunkTask);

        return Result.success(new ChunkVO(uploadId, ossUploadId));
    }
}
