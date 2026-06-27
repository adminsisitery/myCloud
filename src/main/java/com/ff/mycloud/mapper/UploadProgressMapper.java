package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.model.vo.UploadProgressVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 功能描述：上传进度映射器，负责将上传任务实体转换为上传进度视图对象。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper(componentModel = "spring")
public interface UploadProgressMapper {

    /**
     * 功能：将上传任务实体转换为上传进度视图对象。
     *
     * @param task 上传任务实体
     * @return 上传进度视图对象
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
