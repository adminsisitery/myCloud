package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/20 15:14
 * @version 1.0
 */

@Data
public class EmailSendDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "不符合邮箱格式")
    private String email;
}
