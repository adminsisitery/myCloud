package com.cloudrive.fileAsyncService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper（异步服务只读）
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
