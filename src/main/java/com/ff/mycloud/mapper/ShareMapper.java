package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.model.vo.ShareFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface ShareMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fileId", source = "fileId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "shareCode", source = "shareCode")
    @Mapping(target = "password", source = "password")
    @Mapping(target = "expireTime", source = "expireTime")
    @Mapping(target = "isExpired", constant = "false")
    @Mapping(target = "visitCount", constant = "0")
    @Mapping(target = "createdAt" ,source = "now")
    @Mapping(target = "updatedAt" ,source = "now")
    @Mapping(target = "createTime", expression = "java(java.time.LocalDateTime.now())")
    ShareRecord toShareRecord(Long fileId, Long userId, String shareCode, String password, LocalDateTime expireTime, LocalDateTime now);

    @Mapping(target = "shareCode", source = "shareRecord.shareCode")
    @Mapping(target = "expireTime", source = "shareRecord.expireTime")
    @Mapping(target = "visitCount", source = "shareRecord.visitCount")
    @Mapping(target = "createTime", source = "shareRecord.createTime")
    @Mapping(target = "isExpired", source = "shareRecord.isExpired")
    @Mapping(target = "password", source = "shareRecord.password")

    @Mapping(target = "fileId", source = "fileId")
    @Mapping(target = "filename", source = "filename")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "isFolder", source = "isFolder")
    @Mapping(target = "hasPassword",
            expression = "java(shareRecord.getPassword() != null && !shareRecord.getPassword().isEmpty())")
    ShareFileVO toShareFileVO(
            ShareRecord shareRecord,
            String filename,
            Long fileSize,
            Long fileId,
            Boolean isFolder);
}
