package com.cloudrive.aiService.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.aiService.conversation.vo.ConversationVO;
import com.cloudrive.aiService.conversation.vo.MessageVO;
import com.cloudrive.aiService.persistence.entity.AiConversation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    List<ConversationVO> getConversationListByUserId(@Param("UserId") Long userId);

    /** 分页查会话列表 */
    Page<ConversationVO> getConversationListByUserId(Page<ConversationVO> page, @Param("UserId") Long userId);

    void update(@Param("CId") Long conversationId, @Param("name") String name);

    List<MessageVO> selectByCId(@Param("CId") Long conversationId);

    /** 分页查消息列表（按时间倒序） */
    Page<MessageVO> selectByCId(Page<MessageVO> page, @Param("CId") Long conversationId);

}
