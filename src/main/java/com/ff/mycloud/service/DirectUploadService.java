package com.ff.mycloud.service;

import com.ff.mycloud.model.dto.DirectUploadInitDTO;
import com.ff.mycloud.model.vo.ChunkCompleteVO;
import com.ff.mycloud.model.vo.DirectUploadInitVO;

public interface DirectUploadService {
    DirectUploadInitVO init(DirectUploadInitDTO request);
    ChunkCompleteVO complete(String taskId);
    void cancel(String taskId);
    void cleanupExpired();
}
