package com.cloudrive.aiService.conversation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.aiService.conversation.dto.RenameRequest;
import com.cloudrive.aiService.conversation.vo.ConversationVO;
import com.cloudrive.aiService.conversation.vo.MessageVO;
import java.util.List;

public interface AiConversationService {

    ConversationVO createConversation();

    List<ConversationVO> getConversationList();

    Page<ConversationVO> getConversationList(int page, int size);

    void renameConversation(RenameRequest renameRequest);

    void deleteConversation(Long conversationId);

    List<MessageVO> getMessageList(Long conversationId);

    Page<MessageVO> getMessageList(Long conversationId, int page, int size);

}
