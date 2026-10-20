package com.cloudrive.fileTaskService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileTaskService.model.entity.ShareRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分享记录 Mapper（分享过期标记专用）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Mapper
public interface ShareRecordMapper extends BaseMapper<ShareRecord> {
}
