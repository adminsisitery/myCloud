package com.cloudrive.fileAsyncService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（异步服务消费 log.queue 后落库）
 */
@Data
@TableName("operation_log")
public class OperationLog {

    public static final String OP_UPLOAD = "UPLOAD";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_RENAME = "RENAME";
    public static final String OP_SHARE_CREATE = "SHARE_CREATE";
    public static final String OP_SHARE_CANCEL = "SHARE_CANCEL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String operation;

    private Long fileId;

    private String filename;

    private Long fileSize;

    private String extraInfo;

    private String clientIp;

    /** 生产端生成的消息 ID，用于消费幂等 */
    private String msgId;

    private LocalDateTime createTime;
}
