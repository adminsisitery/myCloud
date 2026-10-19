package com.cloudrive.fileManageService.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站列表项视图对象
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class TrashListVO {

    /** 文件 ID */
    private Long id;

    /** 文件（夹）名称（文件已补全后缀） */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（MIME） */
    private String fileType;

    /** 是否为文件夹 */
    private Boolean isFolder;

    /** 文件后缀名 */
    private String fileSuffix;

    /** 删除时间 */
    private LocalDateTime deletedAt;

    /** 剩余保留天数（30 天回收站规则） */
    private Long remainingDays;
}
