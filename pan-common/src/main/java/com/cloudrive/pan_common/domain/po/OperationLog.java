package com.cloudrive.pan_common.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * <p>
 * 记录用户对文件的各种操作行为（上传、删除、重命名、分享创建/取消等）。
 * 数据通过MQ异步消费后写入，msgId字段用于MQ消费的幂等去重。
 * 文件名字段冗余存储以避免后续关联查询。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Data
@TableName("operation_log")
public class OperationLog {

    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作类型：UPLOAD / DELETE / RENAME / SHARE_CREATE / SHARE_CANCEL */
    private String operation;

    /** 文件ID（可为空，如取消分享时无文件变更） */
    private Long fileId;

    /** 文件名（冗余字段，避免关联查询FileInfo表） */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 附加信息（JSON格式，可存储旧文件名、分享码等扩展数据） */
    private String extraInfo;

    /** 客户端IP地址 */
    private String clientIp;

    /** 消息唯一ID，用于MQ消费的幂等去重 */
    private String msgId;

    /** 操作时间 */
    private LocalDateTime createTime;

    // ===== 操作类型常量 =====
    /** 上传文件 */
    public static final String OP_UPLOAD = "UPLOAD";
    /** 删除文件 */
    public static final String OP_DELETE = "DELETE";
    /** 重命名文件 */
    public static final String OP_RENAME = "RENAME";
    /** 创建分享 */
    public static final String OP_SHARE_CREATE = "SHARE_CREATE";
    /** 取消分享 */
    public static final String OP_SHARE_CANCEL = "SHARE_CANCEL";
}
