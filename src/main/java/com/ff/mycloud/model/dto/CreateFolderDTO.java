package com.ff.mycloud.model.dto;

import lombok.Data;

/**
 * 创建文件夹DTO
 * <p>
 * 功能描述：用于接收创建文件夹时的请求参数，包含父文件夹ID和新文件夹名称。
 *
 * @author 飞飞
 * @date 2026/3/10 21:24
 * @version 1.0
 */
@Data
public class CreateFolderDTO {
    /** 父文件夹ID，若为null或0则表示在根目录下创建 */
    private Long parentId;
    /** 新文件夹的名称 */
    private String folderName;
}
