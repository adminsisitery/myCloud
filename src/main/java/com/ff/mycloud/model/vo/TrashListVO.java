package com.ff.mycloud.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站列表视图对象
 * <p>用于展示回收站中的文件/文件夹信息，包含删除时间和剩余天数。</p>
 *
 * @author 飞飞
 * @date 2026/06/30
 */
@Data
public class TrashListVO {

    /** 文件唯一标识ID */
    private Long id;

    /** 文件（夹）名称 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件MIME类型 */
    private String fileType;

    /** 是否为文件夹 */
    private Boolean isFolder;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 进入回收站的时间 */
    private LocalDateTime deletedAt;

    /** 剩余恢复天数（30天倒计时），过期后将自动清理 */
    private Long remainingDays;
}
