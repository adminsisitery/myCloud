package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.model.vo.UploadProgressVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/3 18:28
 * @version 1.0
 */

@Mapper(componentModel = "spring")
public interface UploadProgressMapper {
    /**
     * 将UploadTask转换为UploadProgressVO
     */
    @Mapping(target = "taskId", source = "id")
    @Mapping(target = "filename", source = "filename")
    @Mapping(target = "totalSize", source = "totalSize")
    @Mapping(target = "bytesTransferred", source = "bytesTransferred")
    @Mapping(target = "progress", source = "progress")
    @Mapping(target = "completed", source = "completed")
    @Mapping(target = "success", source = "success")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "message", source = "message")
    UploadProgressVO toUploadProgressVO(UploadTask task) ;
}
