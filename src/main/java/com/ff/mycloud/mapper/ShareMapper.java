package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.model.vo.ShareFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 功能描述：分享记录映射器，负责分享相关实体与视图对象之间的转换。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper(componentModel = "spring")
public interface ShareMapper {

    /**
     * 功能：将分享参数转换为分享记录实体。
     *
     * @param fileId     文件ID
     * @param userId     用户ID
     * @param shareCode  分享码
     * @param password   分享密码
     * @param expireTime 过期时间
     * @param now        当前时间
     * @return 分享记录实体
     */
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

    /**
     * 功能：将分享记录及文件信息转换为分享文件视图对象。
     *
     * @param shareRecord 分享记录实体
     * @param filename    文件名
     * @param fileSize    文件大小
     * @param fileId      文件ID
     * @param isFolder    是否为文件夹
     * @return 分享文件视图对象
     */
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
