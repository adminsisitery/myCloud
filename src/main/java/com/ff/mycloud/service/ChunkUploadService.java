package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.entity.ChunkTask;
import com.ff.mycloud.model.vo.ChunkVO;

public interface ChunkUploadService {

    Result<ChunkVO> initUpload(ChunkFile chunkFile);

}
