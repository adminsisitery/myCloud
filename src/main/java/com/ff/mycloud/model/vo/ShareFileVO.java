package com.ff.mycloud.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareFileVO {
    private String shareCode;
    private LocalDateTime expireTime;
    private Boolean hasPassword;
    private String password;
    private Long fileId;
    private String filename;
    private Long fileSize;
    private LocalDateTime createTime;
    private Integer visitCount;
    private Boolean isExpired;
    private Boolean isFolder;
} 