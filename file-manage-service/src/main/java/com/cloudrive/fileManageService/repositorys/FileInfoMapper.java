package com.cloudrive.fileManageService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper 接口
 * <p>
 * 提供 t_file_info 的基础 CRUD 数据访问能力。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
