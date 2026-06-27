package com.ff.mycloud.repositorys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.model.entity.ShareRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分享记录Mapper接口
 * 功能描述：提供分享记录的基础CRUD数据访问操作
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper
public interface ShareRecordMapper extends BaseMapper<ShareRecord> {

}
