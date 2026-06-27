package com.ff.mycloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作类型：UPLOAD / DELETE / RENAME / SHARE_CREATE / SHARE_CANCEL */
    private String operation;

    /** 文件ID（可为空，如取消分享时无文件变更） */
    private Long fileId;

    /** 文件名（冗余，避免关联查询） */
    private String filename;

    /** 文件大小 */
    private Long fileSize;

    /** 附加信息（JSON：旧文件名、分享码等） */
    private String extraInfo;

    /** 客户端IP */
    private String clientIp;

    /** 消息唯一ID（MQ 幂等去重） */
    private String msgId;

    /** 操作时间 */
    private LocalDateTime createTime;

    // ===== 操作类型常量 =====
    public static final String OP_UPLOAD = "UPLOAD";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_RENAME = "RENAME";
    public static final String OP_SHARE_CREATE = "SHARE_CREATE";
    public static final String OP_SHARE_CANCEL = "SHARE_CANCEL";
}
