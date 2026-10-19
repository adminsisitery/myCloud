package com.cloudrive.fileManageService.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近访问记录实体类
 * <p>
 * 映射数据库表 t_recent_access：记录用户最近访问（下载）过的文件，
 * 供「最近访问」列表展示。下载服务在开始下载前通过内部接口回调本服务写入。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@TableName("t_recent_access")
public class RecentAccess {

    /** 主键，自增 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 文件 ID */
    private Long fileId;

    /** 最近访问时间 */
    private LocalDateTime accessedAt;
}
