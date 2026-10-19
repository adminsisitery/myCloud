package com.cloudrive.fileShareService.mapper;

import com.cloudrive.fileShareService.model.entity.ShareRecord;
import com.cloudrive.fileShareService.model.vo.ShareFileVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 分享记录映射器
 * <p>
 * 负责分享参数 -> ShareRecord 实体、ShareRecord + 文件信息 -> ShareFileVO 的转换。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper(componentModel = "spring")
public interface ShareMapper {

    /**
     * 将分享参数转换为分享记录实体
     *
     * @param fileId     文件 ID
     * @param userId     用户 ID
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
    @Mapping(target = "createdAt", source = "now")
    @Mapping(target = "updatedAt", source = "now")
    @Mapping(target = "createTime", expression = "java(java.time.LocalDateTime.now())")
    ShareRecord toShareRecord(Long fileId, Long userId, String shareCode, String password,
                              LocalDateTime expireTime, LocalDateTime now);

    /**
     * 将分享记录及文件信息转换为分享文件视图对象
     *
     * @param shareRecord 分享记录实体
     * @param filename    文件名
     * @param fileSize    文件大小
     * @param fileId      文件 ID
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
    ShareFileVO toShareFileVO(ShareRecord shareRecord,
                              String filename,
                              Long fileSize,
                              Long fileId,
                              Boolean isFolder);
}
