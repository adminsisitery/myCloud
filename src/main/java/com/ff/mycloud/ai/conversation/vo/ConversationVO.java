package com.ff.mycloud.ai.conversation.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ConversationVO {

    private String mode;

    private Long id;

    private String title;

    private String model;

    private Long messageCount;

    private Long totalTokens;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
