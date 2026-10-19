package com.cloudrive.pan_common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志消息（跨服务共享），消费端落库 operation_log 表
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作用户 */
    private Long userId;

    /** 操作类型：UPLOAD / DELETE / RENAME / SHARE_CREATE / SHARE_CANCEL */
    private String operation;

    /** 文件ID */
    private Long fileId;

    /** 文件名 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 附加信息 */
    private String extraInfo;

    /** 客户端 IP */
    private String clientIp;

    /** 生产端生成的消息ID，用于消费幂等 */
    private String msgId;

    /** 操作时间 */
    private LocalDateTime createTime;
}
