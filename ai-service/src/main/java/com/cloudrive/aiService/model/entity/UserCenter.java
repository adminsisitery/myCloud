package com.cloudrive.aiService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户空间中心实体（ai-service 只读副本）
 * <p>
 * 映射 {@code t_user_center}，仅用于 Agent 的 getStorageInfo 工具查询已用/总空间。
 * 空间配额的扣减与归还不直接写本表，而是调用 user-service 的
 * {@code /internal/space/**} 接口，保持空间归属单一。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@TableName("t_user_center")
public class UserCenter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long totalSpace;

    private Long usedSpace;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
