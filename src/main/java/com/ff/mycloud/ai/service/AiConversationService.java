package com.ff.mycloud.ai.service;

import com.ff.mycloud.ai.model.dto.RenameRequest;
import com.ff.mycloud.ai.model.vo.ConversationVO;
import com.ff.mycloud.ai.model.vo.MessageVO;

import java.util.List;

public interface AiConversationService {

    ConversationVO createConversation();

    List<ConversationVO> getConversationList();

    void renameConversation(RenameRequest renameRequest);

    void deleteConversation(Long conversationId);

    List<MessageVO> getMessageList(Long conversationId);

}
