package com.cloudrive.fileService.service;

import com.cloudrive.fileService.model.dto.DirectUploadInitDTO;
import com.cloudrive.fileService.model.vo.ChunkCompleteVO;
import com.cloudrive.fileService.model.vo.DirectUploadInitVO;

public interface DirectUploadService {
    DirectUploadInitVO init(DirectUploadInitDTO request);
    ChunkCompleteVO complete(String taskId);
    void cancel(String taskId);
    void cleanupExpired();
}
