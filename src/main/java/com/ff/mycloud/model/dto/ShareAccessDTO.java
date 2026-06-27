package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分享访问DTO
 * <p>
 * 功能描述：用于接收访问分享文件时输入的提取码。密码必须为6位且不能为空。
 *
 * @author 飞飞
 * @date 2026/6/27
 * @version 1.0
 */
@Data
public class ShareAccessDTO {
    /** 分享提取码，不能为空，长度必须为6位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 6, message = "密码长度必须为6位")
    private String password;
}