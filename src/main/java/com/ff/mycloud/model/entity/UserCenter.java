package com.ff.mycloud.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/7 17:03
 * @version 1.0
 */

@Data
@TableName("user_center")
public class UserCenter {
    @TableId
    private long id;
    private long userId;
    private String username;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createTime;
    private long usedSpace;
    private long totalSpace;
}
