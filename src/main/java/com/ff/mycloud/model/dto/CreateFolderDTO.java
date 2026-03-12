package com.ff.mycloud.model.dto;

import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/10 21:24
 * @version 1.0
 */

@Data
public class CreateFolderDTO {
    private Long parentId;
    private String folderName;
}
