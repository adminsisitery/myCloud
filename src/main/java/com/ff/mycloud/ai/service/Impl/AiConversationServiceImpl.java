package com.ff.mycloud.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ff.mycloud.ai.mapper.AiConversationMapper;
import com.ff.mycloud.ai.mapper.AiMessageMapper;
import com.ff.mycloud.ai.model.dto.RenameRequest;
import com.ff.mycloud.ai.model.entity.AiConversation;
import com.ff.mycloud.ai.model.vo.ConversationVO;
import com.ff.mycloud.ai.model.vo.MessageVO;
import com.ff.mycloud.ai.service.AiConversationService;
import com.ff.mycloud.common.util.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiConversationServiceImpl implements AiConversationService {

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;

    public AiConversationServiceImpl(AiConversationMapper aiConversationMapper, AiMessageMapper aiMessageMapper) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
    }

    @Override
    public ConversationVO createConversation() {
        Long userId = UserContext.getCurrentUserId();
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setModel("deepseek-chat");
        aiConversationMapper.insert(conversation);

        ConversationVO vo = new ConversationVO();
        BeanUtil.copyProperties(conversation, vo);
        return vo;
    }

    @Override
    public List<ConversationVO> getConversationList() {
        Long userId = UserContext.getCurrentUserId();
        return aiConversationMapper.getConversationListByUserId(userId);
    }

    @Override
    public Page<ConversationVO> getConversationList(int page, int size) {
        Long userId = UserContext.getCurrentUserId();
        return aiConversationMapper.getConversationListByUserId(new Page<>(page, size), userId);
    }

    @Override
    public void renameConversation(RenameRequest request) {
        Long userId = UserContext.getCurrentUserId();
        AiConversation conv = aiConversationMapper.selectById(request.getConversationId());
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该会话");
        }
        aiConversationMapper.update(request.getConversationId(), request.getName());
    }

    @Override
    public void deleteConversation(Long conversationId) {
        Long userId = UserContext.getCurrentUserId();
        AiConversation conv = aiConversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该会话");
        }
        aiConversationMapper.deleteById(conversationId);
        aiMessageMapper.deleteByCId(conversationId);
    }

    @Override
    public List<MessageVO> getMessageList(Long conversationId) {
        Long userId = UserContext.getCurrentUserId();
        checkConversationAccess(userId, conversationId);
        return aiConversationMapper.selectByCId(conversationId);
    }

    @Override
    public Page<MessageVO> getMessageList(Long conversationId, int page, int size) {
        Long userId = UserContext.getCurrentUserId();
        checkConversationAccess(userId, conversationId);
        return aiConversationMapper.selectByCId(new Page<>(page, size), conversationId);
    }

    private void checkConversationAccess(Long userId, Long conversationId) {
        AiConversation conv = aiConversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
    }
}
