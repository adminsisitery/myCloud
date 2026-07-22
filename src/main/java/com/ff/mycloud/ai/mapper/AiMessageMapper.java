package com.ff.mycloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.ai.model.entity.AiMessages;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessages> {

    List<AiMessages> findByMsgId(@Param("memId") Long memId);

    void deleteByCId(@Param("CId") Long conversationId);

}
