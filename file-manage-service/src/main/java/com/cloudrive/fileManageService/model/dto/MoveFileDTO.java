package com.cloudrive.fileManageService.model.dto;

import lombok.Data;

/**
 * 移动文件/文件夹请求参数（契约与原单体一致）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class MoveFileDTO {

    /** 目标父文件夹 ID，0 或 null 表示根目录 */
    private Long targetParentId;
}
