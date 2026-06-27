package com.ff.mycloud.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.mapping.FetchType;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 分享记录实体类
 * <p>映射数据库表 t_share_record，记录用户分享文件或文件夹的信息。
 * 包括分享码、访问密码、过期时间、访问次数等属性，支持安全的文件外部分享。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
@TableName("t_share_record")
public class ShareRecord {
    /** 主键，自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被分享的文件（夹）ID */
    private Long fileId;

    /** 分享发起者用户ID */
    private Long userId;

    /** 分享码，用于生成分享链接的唯一标识 */
    private String shareCode;

    /** 分享访问密码，为空表示无密码分享 */
    private String password;

    /** 分享过期时间 */
    private LocalDateTime expireTime;

    /** 分享创建时间 */
    private LocalDateTime createTime;

    /** 是否已过期，默认false */
    private Boolean isExpired = false;

    /** 分享链接被访问次数，默认0 */
    private Integer visitCount = 0;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
} 