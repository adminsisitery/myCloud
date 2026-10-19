package com.cloudrive.fileService.mapper;

import com.cloudrive.fileService.model.entity.ChunkFile;
import com.cloudrive.fileService.model.entity.ChunkTask;
import org.mapstruct.Mapper;

@Mapper(componentModel="spring")
public interface ChunkUploadMapper {

    ChunkTask toChunkTask(ChunkFile chunkFile, String uploadId);
}
