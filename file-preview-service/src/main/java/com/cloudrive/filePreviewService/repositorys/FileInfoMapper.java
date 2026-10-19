package com.cloudrive.filePreviewService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.filePreviewService.model.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper（预览服务只读）
 * <p>预览链路只需要按 id + userId 查询单条记录，因此没有携带 XML 自定义 SQL。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
