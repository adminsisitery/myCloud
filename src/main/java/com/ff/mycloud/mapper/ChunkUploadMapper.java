package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.entity.ChunkTask;
import org.mapstruct.Mapper;

@Mapper(componentModel="spring")
public interface ChunkUploadMapper {

    ChunkTask toChunkTask(ChunkFile chunkFile, String uploadId);
}
