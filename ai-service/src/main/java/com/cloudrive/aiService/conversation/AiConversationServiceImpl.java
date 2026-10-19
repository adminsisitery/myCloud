package com.cloudrive.aiService.conversation;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.aiService.conversation.dto.RenameRequest;
import com.cloudrive.aiService.conversation.vo.ConversationVO;
import com.cloudrive.aiService.conversation.vo.MessageVO;
import com.cloudrive.aiService.persistence.entity.AiConversation;
import com.cloudrive.aiService.persistence.mapper.AiConversationMapper;
import com.cloudrive.aiService.persistence.mapper.AiMessageMapper;
import com.cloudrive.pan_common.util.UserContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiConversationServiceImpl implements AiConversationService {

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AiConversationServiceImpl(AiConversationMapper aiConversationMapper, AiMessageMapper aiMessageMapper, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.jdbc = jdbc;
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
    @org.springframework.transaction.annotation.Transactional
    public void deleteConversation(Long conversationId) {
        Long userId = UserContext.getCurrentUserId();
        jdbc.queryForList("SELECT id FROM t_ai_conversation WHERE id=? AND user_id=? FOR UPDATE",conversationId,userId);
        AiConversation conv = aiConversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该会话");
        }
        Integer running = jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_agent_run WHERE conversation_id=? AND status IN ('QUEUED','RUNNING')",Integer.class,conversationId);
        Integer chatting = jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_chat_memory WHERE conversation_id=? AND lease_id IS NOT NULL AND lease_until>CURRENT_TIMESTAMP",Integer.class,conversationId);
        if (chatting!=null && chatting>0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"请先结束当前聊天再删除会话");
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_agent_action a JOIN t_ai_agent_run r ON r.id=a.run_id WHERE r.conversation_id=? AND a.status IN ('PENDING','EXECUTING')",Integer.class,conversationId);
        if ((running!=null && running>0) || (pending!=null && pending>0)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"请先停止Agent任务并处理待确认操作");
        jdbc.update("DELETE FROM t_ai_agent_action WHERE run_id IN (SELECT id FROM t_ai_agent_run WHERE conversation_id=?)",conversationId);
        jdbc.update("DELETE FROM t_ai_agent_run WHERE conversation_id=?",conversationId);
        jdbc.update("DELETE FROM t_ai_chat_turn WHERE conversation_id=? AND user_id=?",conversationId,userId);
        jdbc.update("DELETE FROM t_ai_chat_memory WHERE conversation_id=? AND user_id=?",conversationId,userId);
        aiMessageMapper.deleteByCId(conversationId);
        aiConversationMapper.deleteById(conversationId);
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
