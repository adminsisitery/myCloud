package com.ff.mycloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.vo.ConversationVO;
import com.ff.mycloud.ai.model.vo.MessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    List<ConversationVO> getConversationListByUserId(@Param("UserId") Long userId);

    void update(@Param("CId") Long conversationId, @Param("name") String name);

    List<MessageVO> selectByCId(@Param("CId") Long conversationId);

}
