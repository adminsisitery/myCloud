package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 * <p>
 * 功能描述：用于接收用户登录的请求参数，包含用户名和密码。两个字段均经过非空校验。
 *
 * @author 飞飞
 * @date 2026/1/11 21:44
 * @version 1.0
 */
@Data
public class LoginDTO {
    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;
    /** 密码，不能为空 */
    @NotBlank(message = "密码不能为空")
    private String password;
}