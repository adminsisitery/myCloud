package com.cloudrive.userService.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户中心信息实体类
 * <p>映射数据库表 user_center，记录每个用户的云盘空间使用情况，
 * 包括已用空间和总空间配额信息。
 *
 * @author 飞飞
 * @date 2026/3/7 17:03
 * @version 1.0
 */
@Data
@TableName("user_center")
public class UserCenter {
    /** 主键，自增ID。使用包装类型 Long，避免 MyBatis-Plus 对原始类型主键的告警 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的用户ID */
    private long userId;
    /** 用户名 */
    private String username;
    /** 用户注册时间，JSON序列化时格式为 yyyy-MM-dd */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;
    /** 已使用的存储空间（字节） */
    private long usedSpace;
    /** 总存储空间配额（字节） */
    private long totalSpace;
}
