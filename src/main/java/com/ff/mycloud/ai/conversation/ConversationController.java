package com.ff.mycloud.ai.conversation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ff.mycloud.ai.conversation.dto.RenameRequest;
import com.ff.mycloud.ai.conversation.vo.ConversationVO;
import com.ff.mycloud.ai.conversation.vo.MessageVO;
import com.ff.mycloud.common.result.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/conversations")
public class ConversationController {

    private final AiConversationService conversationService;

    public ConversationController(AiConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** 获取当前用户会话列表（不分页） */
    @GetMapping
    public Result<List<ConversationVO>> getConversationList() {
        return Result.success(conversationService.getConversationList());
    }

    /** 获取当前用户会话列表（分页） */
    @GetMapping("/page")
    public Result<Page<ConversationVO>> getConversationListPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(conversationService.getConversationList(page, size));
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

    /** 获取会话的消息列表（不分页） */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> getMessageList(@PathVariable Long id) {
        return Result.success(conversationService.getMessageList(id));
    }

    /** 获取会话的消息列表（分页，按时间倒序） */
    @GetMapping("/{id}/messages/page")
    public Result<Page<MessageVO>> getMessageListPaginated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Result.success(conversationService.getMessageList(id, page, size));
    }
}
