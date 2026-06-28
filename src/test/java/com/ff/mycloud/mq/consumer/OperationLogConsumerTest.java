package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.service.OperationLogMapperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

/**
 * OperationLogConsumer 单元测试
 * <p>测试MQ消费者在去重后的行为：重复消息跳过，首次消息入库</p>
 */
class OperationLogConsumerTest {

    @Mock private OperationLogMapperService operationLogMapperService;
    @Mock private MessageDedupService messageDedupService;

    private OperationLogConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new OperationLogConsumer(operationLogMapperService, messageDedupService);
    }

    // ==================== 正常流程 ====================

    @Test
    @DisplayName("首次消息：去重通过 → 应保存到数据库")
    void shouldSaveWhenFirstDelivery() {
        OperationLog log = buildLog("msg-001");
        when(messageDedupService.tryConsume("msg-001")).thenReturn(true);

        consumer.handleLog(log);

        verify(operationLogMapperService).save(log);
    }

    @Test
    @DisplayName("重复消息：去重失败 → 应跳过不保存")
    void shouldSkipWhenDuplicateDelivery() {
        OperationLog log = buildLog("msg-002");
        when(messageDedupService.tryConsume("msg-002")).thenReturn(false);

        consumer.handleLog(log);

        verify(operationLogMapperService, never()).save(any());
    }

    @Test
    @DisplayName("消息 msgId 为 null → 放行并保存")
    void shouldSaveWhenMsgIdIsNull() {
        OperationLog log = buildLog(null);
        when(messageDedupService.tryConsume(null)).thenReturn(true);

        consumer.handleLog(log);

        verify(operationLogMapperService).save(log);
    }

    // ==================== 交互验证 ====================

    @Test
    @DisplayName("保存前必须先执行去重检查")
    void shouldCheckDedupBeforeSave() {
        OperationLog log = buildLog("msg-003");
        when(messageDedupService.tryConsume("msg-003")).thenReturn(true);

        consumer.handleLog(log);

        // 验证调用顺序：先去重，再保存
        var inOrder = inOrder(messageDedupService, operationLogMapperService);
        inOrder.verify(messageDedupService).tryConsume("msg-003");
        inOrder.verify(operationLogMapperService).save(log);
    }

    @Test
    @DisplayName("重复消息不应调用 save（哪怕一次）")
    void shouldNeverCallSaveForDuplicate() {
        OperationLog log = buildLog("msg-004");
        when(messageDedupService.tryConsume("msg-004")).thenReturn(false);

        consumer.handleLog(log);

        verify(operationLogMapperService, never()).save(any());
    }

    // ==================== 辅助方法 ====================

    private OperationLog buildLog(String msgId) {
        OperationLog log = new OperationLog();
        log.setUserId(1L);
        log.setOperation(OperationLog.OP_UPLOAD);
        log.setFileId(100L);
        log.setFilename("test.txt");
        log.setFileSize(1024L);
        log.setMsgId(msgId);
        return log;
    }
}
