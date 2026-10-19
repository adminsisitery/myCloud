package com.cloudrive.fileShareService.model.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建分享 DTO
 * <p>
 * 接收创建文件分享的请求参数：被分享文件 ID、过期时间与提取密码。
 * 过期时间不能早于当前时间，提取密码为 6 位数字和字母组合。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
public class ShareCreateDTO {

    /** 要分享的文件 ID，不能为空 */
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /** 分享过期时间，不能为空且不能早于当前时间 */
    @NotNull(message = "过期时间不能为空")
    @FutureOrPresent(message = "过期时间不能早于当前时间")
    private LocalDateTime expireTime;

    /** 分享提取密码，必须为 6 位数字和字母组合（可为空表示无密码分享） */
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6}$", message = "密码必须是6位数字和字母组合")
    private String password;
}
