package com.ff.mycloud.ai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long id;

    private String title;

    private String model;

    private Long messageCount;

    private Long totalTokens;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
