package com.ff.mycloud.ai.controller;

import com.ff.mycloud.ai.model.dto.RenameRequest;
import com.ff.mycloud.ai.model.vo.ConversationVO;
import com.ff.mycloud.ai.model.vo.MessageVO;
import com.ff.mycloud.ai.service.AiConversationService;
import com.ff.mycloud.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/conversations")
public class ConversationController {

    private final AiConversationService conversationService;

    public ConversationController(AiConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** 获取当前用户会话列表 */
    @GetMapping
    public Result<List<ConversationVO>> getConversationList() {
        return Result.success(conversationService.getConversationList());
    }

    /** 创建新会话 */
    @PostMapping
    public Result<ConversationVO> createConversation() {
        return Result.success(conversationService.createConversation());
    }

    /** 重命名会话 */
    @PutMapping("/{id}")
    public Result<Void> renameConversation(@PathVariable Long id,
                                           @RequestBody @Valid RenameRequest request) {
        request.setConversationId(id);
        conversationService.renameConversation(request);
        return Result.success();
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }

    /** 获取会话的消息列表 */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> getMessageList(@PathVariable Long id) {
        return Result.success(conversationService.getMessageList(id));
    }
}
