package com.cloudrive.pan_common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用量通知消息（跨服务共享），消费端通过 WebSocket + 邮件提醒用户空间不足
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 已用空间（字节） */
    private Long usageSize;

    /** 总空间配额（字节） */
    private Long totalSize;

    /** 通知文本 */
    private String message;
}
