package com.cloudrive.fileSearchService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileSearchService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_file_info 数据访问接口（只读）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
