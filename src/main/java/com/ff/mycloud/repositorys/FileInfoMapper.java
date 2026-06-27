package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息Mapper接口
 * 功能描述：提供文件信息的基础CRUD数据访问操作
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
