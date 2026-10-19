package com.cloudrive.fileShareService.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分享视图对象
 * <p>
 * 封装分享码、过期时间、密码保护、被分享文件元数据以及访问统计等信息。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class ShareFileVO {

    /** 分享码，接收方通过该码访问被分享的文件 */
    private String shareCode;

    /** 分享的过期时间，超过该时间后分享链接失效 */
    private LocalDateTime expireTime;

    /** 是否设置了访问密码，true 表示需要密码才能访问 */
    private Boolean hasPassword;

    /** 分享的访问密码（仅在当前用户为分享者时返回） */
    private String password;

    /** 被分享的文件 ID */
    private Long fileId;

    /** 被分享的文件名称 */
    private String filename;

    /** 被分享的文件大小，单位为字节 */
    private Long fileSize;

    /** 分享的创建时间 */
    private LocalDateTime createTime;

    /** 分享链接的访问次数统计 */
    private Integer visitCount;

    /** 分享是否已过期，true 表示已超过有效期限 */
    private Boolean isExpired;

    /** 被分享的是否为文件夹，true 表示文件夹，false 表示普通文件 */
    private Boolean isFolder;
}
