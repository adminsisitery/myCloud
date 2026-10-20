package com.cloudrive.fileSearchService.mapper;

import com.cloudrive.fileSearchService.model.entity.FileInfo;
import com.cloudrive.fileSearchService.model.vo.FileListVO;
import org.mapstruct.Mapper;

/**
 * 文件领域对象转换器（MapStruct）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Mapper(componentModel = "spring")
public interface FileMapper {

    /**
     * FileInfo 转文件列表项
     *
     * @param fileInfo 文件信息实体
     * @return 文件列表视图对象
     */
    FileListVO toFileListVO(FileInfo fileInfo);
}
