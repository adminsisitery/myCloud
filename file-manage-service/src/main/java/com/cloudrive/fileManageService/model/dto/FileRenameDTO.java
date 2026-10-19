package com.cloudrive.fileManageService.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名请求参数（契约与原单体一致）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class FileRenameDTO {

    /** 新文件名（不含非法字符，最长 50 字符） */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 50, message = "文件名长度不能超过 50 个字符")
    private String newFilename;
}
