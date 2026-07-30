package com.ff.mycloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.ai.model.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    List<AiMessage> findByMsgId(@Param("memId") Long memId);

    void deleteByCId(@Param("CId") Long conversationId);

    /** 查最新一条 ASSISTANT 消息的 id */
    Long findLastAssistantId(@Param("conversationId") Long conversationId);

    /** 更新某条消息的 token 用量 */
    void updateTokens(@Param("id") Long id,
                      @Param("inputTokens") Integer inputTokens,
                      @Param("outputTokens") Integer outputTokens);

}
