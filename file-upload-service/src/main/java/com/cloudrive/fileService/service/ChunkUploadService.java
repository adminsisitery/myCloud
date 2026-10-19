package com.cloudrive.fileService.service;

import com.cloudrive.pan_common.result.Result;
import com.cloudrive.fileService.model.entity.ChunkFile;
import com.cloudrive.fileService.model.vo.ChunkCompleteVO;
import com.cloudrive.fileService.model.vo.ChunkPartVO;
import com.cloudrive.fileService.model.vo.ChunkProgressVO;
import com.cloudrive.fileService.model.vo.ChunkVO;
import org.springframework.web.multipart.MultipartFile;

public interface ChunkUploadService {

    Result<ChunkVO> initUpload(ChunkFile chunkFile);

    /**
     * 上传单个分片
     *
     * @param uploadId   初始化返回的自定义uploadId
     * @param partNumber 分片编号（从1开始）
     * @param chunk      分片文件数据
     * @return 分片上传结果
     */
    Result<ChunkPartVO> uploadPart(String uploadId, Integer partNumber, MultipartFile chunk);

    Result<ChunkCompleteVO> completeChunk(String uploadId);

    Result<ChunkProgressVO> getMissingList(String uploadId);

    Result<String> cancelUpload(String uploadId);
}
