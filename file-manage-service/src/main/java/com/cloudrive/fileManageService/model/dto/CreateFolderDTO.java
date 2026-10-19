package com.cloudrive.fileManageService.model.dto;

import lombok.Data;

/**
 * 新建文件夹请求参数
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class CreateFolderDTO {

    /** 父文件夹 ID，根目录为 0 或 null */
    private Long parentId;

    /** 文件夹名称 */
    private String folderName;
}
