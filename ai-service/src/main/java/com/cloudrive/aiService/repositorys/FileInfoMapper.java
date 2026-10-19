package com.cloudrive.aiService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.aiService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
