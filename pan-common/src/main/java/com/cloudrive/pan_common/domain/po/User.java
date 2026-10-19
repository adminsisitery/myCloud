package com.cloudrive.pan_common.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>映射数据库表 t_user，记录系统用户的账号信息和基本资料。
 * 支持本地注册用户和GitHub第三方登录用户。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
@TableName("t_user")
public class User {

    /** 主键，自增ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户名，用于登录和显示 */
    @TableField("username")
    private String username;

    /** 加密后的用户密码 */
    @TableField("password")
    private String password;

    /** 用户邮箱地址 */
    @TableField("email")
    private String email;

    /** 用户账号状态（如：1正常、0禁用等） */
    @TableField("status")
    private Integer status;

    /** 账号创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 账号最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** GitHub第三方登录的用户唯一标识 */
    private String githubId;

    /** 用户头像URL地址 */
    private String avatar;
}