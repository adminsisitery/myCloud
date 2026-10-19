package com.cloudrive.userService.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮件发送DTO
 * <p>
 * 功能描述：用于接收发送邮件验证码的请求参数，包含目标邮箱地址。邮箱字段经过非空和格式校验。
 *
 * @author 飞飞
 * @date 2026/1/20 15:14
 * @version 1.0
 */
@Data
public class EmailSendDTO {
    /** 目标邮箱地址，不能为空且需符合邮箱格式 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "不符合邮箱格式")
    private String email;
}
