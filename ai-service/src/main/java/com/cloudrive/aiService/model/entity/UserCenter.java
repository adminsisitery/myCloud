package com.cloudrive.aiService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户空间中心实体（ai-service 只读副本）
 * <p>
 * 映射 {@code user_center}，表名与 user-service 侧 {@code UserCenter} 一致。
 * 历史版本这里写的是 {@code t_user_center}，而 user-service 实际读写的是
 * {@code user_center}，两边对不上，Agent 的 getStorageInfo 查到的永远是空表。
 * </p>
 * <p>
 * 空间配额的扣减与归还不直接写本表，而是调用 user-service 的
 * {@code /internal/space/**} 接口，保持空间归属单一。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@TableName("user_center")
public class UserCenter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long totalSpace;

    private Long usedSpace;

    /** user-service 侧列名是 create_time（实体字段 createTime），不是 created_at。 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
