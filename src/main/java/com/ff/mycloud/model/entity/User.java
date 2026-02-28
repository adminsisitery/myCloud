package com.ff.mycloud.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")  // 使用 MyBatis-Plus 的 TableName 注解指定表名
public class User {

    @TableId(value = "id", type = IdType.AUTO)  // MyBatis-Plus 的主键策略，ID 自增
    private Long id;

    @TableField("username")  // 显示指定字段名称映射，通常可以省略，除非字段名与属性名不一致
    private String username;

    @TableField("password")  // 显示指定字段名称映射
    private String password;

    @TableField("email")  // 显示指定字段名称映射
    private String email;

    @TableField("status")  // 显示指定字段名称映射
    private Integer status;

    @TableField("create_time")  // 显示指定字段名称映射
    private LocalDateTime createTime;

    @TableField("update_time")  // 显示指定字段名称映射
    private LocalDateTime updateTime;
}