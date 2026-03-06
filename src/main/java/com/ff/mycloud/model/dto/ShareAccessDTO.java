package com.ff.mycloud.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShareAccessDTO {
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 6, message = "密码长度必须为6位")
    private String password;
} 