package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/11 21:44
 * @version 1.0
 */

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}