package com.ff.mycloud.model.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.ibatis.mapping.FetchType;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@TableName("t_share_record")
public class ShareRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private Long userId;

    private String shareCode;

    private String password;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private Boolean isExpired = false;

    private Integer visitCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
} 