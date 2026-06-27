package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件重命名DTO
 * <p>
 * 功能描述：用于接收文件重命名的请求参数，包含新的文件名。新文件名经过非空和长度校验。
 *
 * @author 飞飞
 * @date 2026/6/27
 * @version 1.0
 */
@Data
public class FileRenameDTO {
    /** 新的文件名，不能为空且最长不超过50个字符 */
    @NotBlank(message = "新文件名不能为空")
    @Size(max = 50, message = "新文件名不能超过50个字符")
    private String newFilename;
}