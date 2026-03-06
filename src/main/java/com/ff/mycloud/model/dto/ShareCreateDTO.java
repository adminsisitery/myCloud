package com.ff.mycloud.model.dto;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareCreateDTO {
    @NotNull(message = "文件ID不能为空")
    private Long fileId;
    @NotNull(message = "过期时间不能为空")
    @FutureOrPresent(message = "过期时间不能早于当前时间")
    private LocalDateTime expireTime;
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6}$", message = "密码必须是6位数字和字母组合")
    private String password;
}