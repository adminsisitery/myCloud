package com.ff.mycloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近访问记录实体
 * <p>记录用户下载/预览文件的时间，用于"最近访问"列表。
 * 同一用户+同一文件只保留一条记录，每次访问更新 accessed_at。</p>
 *
 * @author 飞飞
 * @date 2026/07/01
 */
@Data
@TableName("t_recent_access")
public class RecentAccess {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long fileId;

    private LocalDateTime accessedAt;
}
