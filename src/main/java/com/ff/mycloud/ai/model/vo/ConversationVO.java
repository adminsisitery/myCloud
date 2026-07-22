package com.ff.mycloud.ai.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long id;

    private String title;

    private String model;

    private Long messageCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
