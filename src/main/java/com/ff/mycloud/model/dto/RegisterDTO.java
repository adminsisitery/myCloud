package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 注册请求DTO
 * <p>
 * 功能描述：用于接收用户注册的请求参数，包含用户名、密码、邮箱和验证码。
 * 各字段均经过相应的校验规则，包括长度限制、格式正则匹配等。
 *
 * @author 飞飞
 * @date 2026/1/12 21:57
 * @version 1.0
 */
@Data
public class RegisterDTO {
    /** 用户名，不能为空，长度在3到20个字符之间 */
    @NotBlank(message = "用户名不能为空")
    @Length(min = 3, max = 20, message = "用户名长度必须在3到20个字符之间")
    private String username;

    /** 密码，不能为空，长度在6到20个字符之间，且必须为数字和英文字母组合 */
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度必须在6到20个字符之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须是数字和英文组合")
    private String password;

    /** 邮箱地址，不能为空且需符合邮箱格式 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 邮箱验证码，不能为空 */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
