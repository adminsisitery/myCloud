package com.ff.mycloud.model.entity;

import lombok.Data;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/8 20:27
 * @version 1.0
 */
@Data
public class ChangePasswordDTO {

    private String oldPassword;

    private String newPassword;

}