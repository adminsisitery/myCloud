package com.ff.mycloud.model.dto;

import lombok.Data;

/**
 * 移动文件请求 DTO
 *
 * @author 飞飞
 * @date 2026/06/29
 */
@Data
public class MoveFileDTO {
    /** 目标父目录ID，null或0表示移动到根目录 */
    private Long targetParentId;
}
