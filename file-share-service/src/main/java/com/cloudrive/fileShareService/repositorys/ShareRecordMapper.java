package com.cloudrive.fileShareService.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.fileShareService.model.entity.ShareRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分享记录 Mapper
 * <p>提供 t_share_record 的基础 CRUD 数据访问能力。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper
public interface ShareRecordMapper extends BaseMapper<ShareRecord> {
}
