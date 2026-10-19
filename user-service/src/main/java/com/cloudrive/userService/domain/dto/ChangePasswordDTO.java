package com.cloudrive.userService.domain.dto;

import lombok.Data;

/**
 * 修改密码请求DTO
 * <p>用于接收用户修改密码时提交的新旧密码信息。
 *
 * @author 飞飞
 * @date 2026/3/8 20:27
 * @version 1.0
 */
@Data
public class ChangePasswordDTO {

    /** 用户输入的旧密码 */
    private String oldPassword;

    /** 用户输入的新密码 */
    private String newPassword;

}