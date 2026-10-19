package com.cloudrive.fileShareService.service.impl;

import com.cloudrive.fileShareService.mapper.ShareMapper;
import com.cloudrive.fileShareService.model.entity.FileInfo;
import com.cloudrive.fileShareService.model.entity.ShareRecord;
import com.cloudrive.fileShareService.model.vo.ShareFileVO;
import com.cloudrive.fileShareService.redis.ShareQueueRedis;
import com.cloudrive.fileShareService.redis.ShareTokenRedis;
import com.cloudrive.fileShareService.service.FileInfoMapperService;
import com.cloudrive.fileShareService.service.FileShareService;
import com.cloudrive.fileShareService.service.ShareRecordMapperService;
import com.cloudrive.fileShareService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.ExceptionUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件分享服务实现类
 * <p>
 * 从原单体 {@code shareServiceImpl} 迁移：创建分享（生成 ShareCode + 保存记录 +
 * 加入 Redis 延迟队列）、密码/Token 验证、取消分享、内容下载与过期标记。
 * 与原实现的差异：用户信息改由 pan-common 的 {@link UserContext} 提供；
 * 文件内容读取改由本服务的 {@link StorageService} 直接完成。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FileShareServiceImpl implements FileShareService {

    private static final Logger logger = LoggerFactory.getLogger(FileShareServiceImpl.class);

    private final FileInfoMapperService fileInfoMapperService;
    private final ShareMapper shareMapper;
    private final ShareRecordMapperService shareRecordMapperService;
    private final ShareQueueRedis shareQueueRedis;
    private final ShareTokenRedis shareTokenRedis;
    private final StorageService storageService;

    public FileShareServiceImpl(FileInfoMapperService fileInfoMapperService,
                                ShareMapper shareMapper,
                                ShareRecordMapperService shareRecordMapperService,
                                ShareQueueRedis shareQueueRedis,
                                ShareTokenRedis shareTokenRedis,
                                StorageService storageService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.shareMapper = shareMapper;
        this.shareRecordMapperService = shareRecordMapperService;
        this.shareQueueRedis = shareQueueRedis;
        this.shareTokenRedis = shareTokenRedis;
        this.storageService = storageService;
    }

    /**
     * 取消分享链接：从延迟队列移除、删除数据库记录、删除 Token
     *
     * @param shareCode 分享码
     */
    @Override
    public void cancelShare(String shareCode) {
        Long currentUserId = UserContext.getCurrentUserId();
        ShareRecord record = shareRecordMapperService.findByUserIdAndShareCode(currentUserId, shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        shareQueueRedis.removeFromDelayedQueue(record.getId());
        shareRecordMapperService.removeById(record.getId());
        shareTokenRedis.deleteToken(shareCode);
        logger.info("取消分享完成: userId={}, shareId={}", currentUserId, record.getId());
    }

    /**
     * 创建文件分享
     *
     * @param fileId     待分享的文件 ID
     * @param expireTime 分享过期时间
     * @param password   访问密码（null 或空字符串表示无密码）
     * @return 分享文件 VO
     */
    @Override
    public ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password) {
        String normalizedPassword = (password != null && password.trim().isEmpty()) ? null : password;
        Long currentUserId = UserContext.getCurrentUserId();

        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ExceptionUtil.throwIf(
                !fileInfo.getUserId().equals(currentUserId),
                ErrorCode.NO_SHARE_PERMISSION
        );

        String shareCode = UUID.randomUUID().toString().replace("-", "");
        ShareRecord shareRecord = shareMapper.toShareRecord(
                fileInfo.getId(), currentUserId, shareCode, normalizedPassword, expireTime, LocalDateTime.now());
        shareRecordMapperService.save(shareRecord);
        addToDelayedQueue(shareRecord.getId(), expireTime);

        logger.info("创建分享完成: userId={}, fileId={}, shareId={}",
                currentUserId, fileId, shareRecord.getId());
        return shareMapper.toShareFileVO(
                shareRecord, fileInfo.getFilename(), fileInfo.getFileSize(), fileId, fileInfo.getIsFolder());
    }

    /**
     * 获取当前用户所有未过期的分享文件列表
     *
     * @return 分享文件 VO 列表
     */
    @Override
    public List<ShareFileVO> getSharedFiles() {
        Long userId = UserContext.getCurrentUserId();
        List<ShareRecord> records = shareRecordMapperService.findByUserIdAndNotDel(userId);
        List<ShareFileVO> result = new ArrayList<>(records.size());
        for (ShareRecord record : records) {
            FileInfo file = fileInfoMapperService.getById(record.getFileId());
            if (file == null || Boolean.TRUE.equals(file.getIsDeleted())) {
                logger.warn("分享记录关联的文件已不存在，跳过: shareId={}, fileId={}",
                        record.getId(), record.getFileId());
                continue;
            }
            if (Boolean.TRUE.equals(file.getIsFolder())) {
                result.add(shareMapper.toShareFileVO(
                        record, file.getFilename(), 0L, file.getId(), file.getIsFolder()));
            } else {
                result.add(shareMapper.toShareFileVO(
                        record,
                        file.getFilename() + "." + file.getFileSuffix(),
                        file.getFileSize(),
                        file.getId(),
                        file.getIsFolder()));
            }
        }
        return result;
    }

    /**
     * 验证分享 Token 是否有效
     *
     * @param shareCode 分享码
     * @param token     验证令牌
     * @return true 表示有效
     */
    @Override
    public boolean validateShareToken(String shareCode, String token) {
        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);
        if (shareRecord == null) {
            return false;
        }
        String password = shareRecord.getPassword();
        if (password == null || password.trim().isEmpty()) {
            return true;
        }
        return shareTokenRedis.validateToken(shareCode, token);
    }

    /**
     * 通过 Token 访问分享文件
     *
     * @param shareCode 分享码
     * @param token     访问令牌
     * @return 分享文件 VO
     */
    @Override
    public ShareFileVO accessShareByToken(String shareCode, String token) {
        ExceptionUtil.throwIf(!validateShareToken(shareCode, token), ErrorCode.INVALID_TOKEN);

        ShareRecord record = shareRecordMapperService.findByShareCode(shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        FileInfo fileInfo = fileInfoMapperService.getById(record.getFileId());
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return shareMapper.toShareFileVO(
                record,
                fileInfo.getFilename() + "." + fileInfo.getFileSuffix(),
                fileInfo.getFileSize(),
                fileInfo.getId(),
                fileInfo.getIsFolder());
    }

    /**
     * 通过密码访问分享文件
     *
     * @param shareCode 分享码
     * @param password  明文密码
     * @return 分享文件 VO
     */
    @Override
    public ShareFileVO accessShare(String shareCode, String password) {
        ShareRecord record = shareRecordMapperService.findByShareCode(shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        ExceptionUtil.throwIf(
                Boolean.TRUE.equals(record.getIsExpired())
                        || record.getExpireTime().isBefore(LocalDateTime.now()),
                ErrorCode.SHARE_EXPIRED
        );
        if (record.getPassword() != null && !record.getPassword().isEmpty()) {
            ExceptionUtil.throwIf(password == null, ErrorCode.MISSING_PASSWORD);
            ExceptionUtil.throwIf(!password.equals(record.getPassword()), ErrorCode.INVALID_PASSWORD);
        }

        FileInfo fileInfo = fileInfoMapperService.getById(record.getFileId());
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        shareRecordMapperService.visitedPlus1(record.getVisitCount(), record.getId());
        return shareMapper.toShareFileVO(
                record,
                fileInfo.getFilename() + "." + fileInfo.getFileSuffix(),
                fileInfo.getFileSize(),
                fileInfo.getId(),
                fileInfo.getIsFolder());
    }

    /**
     * 为分享链接生成访问 Token
     *
     * @param shareCode 分享码
     * @param password  明文密码
     * @return 生成的 Token
     */
    @Override
    public String generateShareToken(String shareCode, String password) {
        ShareRecord record = shareRecordMapperService.findByShareCode(shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        if (record.getPassword() != null && !record.getPassword().isEmpty()) {
            ExceptionUtil.throwIf(password == null, ErrorCode.INVALID_PASSWORD);
        }
        return shareTokenRedis.generateAndStoreToken(shareCode);
    }

    /**
     * 下载分享的文件内容（普通文件）
     *
     * @param shareCode 分享码
     * @param token     访问令牌
     * @return 文件字节数组
     */
    @Override
    public byte[] downloadSharedFile(String shareCode, String token) {
        ShareRecord record = shareRecordMapperService.findByShareCode(shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(record.getIsExpired())
                || record.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }
        String password = record.getPassword();
        if (password != null && !password.trim().isEmpty() && !validateShareToken(shareCode, token)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        FileInfo fileInfo = fileInfoMapperService.getById(record.getFileId());
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ExceptionUtil.throwIf(Boolean.TRUE.equals(fileInfo.getIsFolder()), ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        return storageService.downloadFile(fileInfo.getPath());
    }

    /**
     * 根据分享码获取对应文件的完整文件名（含后缀）
     *
     * @param shareCode 分享码
     * @return 文件名
     */
    @Override
    public String getFilename(String shareCode) {
        ShareRecord record = shareRecordMapperService.findByShareCode(shareCode);
        if (record == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        FileInfo fileInfo = fileInfoMapperService.getById(record.getFileId());
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileInfo.getFilename() + "." + fileInfo.getFileSuffix();
    }

    /**
     * 批量标记所有已过期的分享为失效状态
     */
    @Override
    public void markExpiredShares() {
        shareRecordMapperService.lambdaUpdate()
                .lt(ShareRecord::getExpireTime, LocalDateTime.now())
                .eq(ShareRecord::getIsExpired, false)
                .set(ShareRecord::getIsExpired, true)
                .update();
    }

    /**
     * 单条标记指定 ID 的分享为失效
     *
     * @param shareId 分享记录 ID
     * @return true 表示更新成功
     */
    @Override
    public boolean markShareExpired(Long shareId) {
        try {
            return shareRecordMapperService.lambdaUpdate()
                    .eq(ShareRecord::getId, shareId)
                    .lt(ShareRecord::getExpireTime, LocalDateTime.now())
                    .eq(ShareRecord::getIsExpired, false)
                    .set(ShareRecord::getIsExpired, true)
                    .update();
        } catch (RuntimeException e) {
            logger.error("标记分享ID: {} 过期失败", shareId, e);
            return false;
        }
    }

    /**
     * 将分享记录 ID 加入 Redis 延迟队列
     *
     * @param id         分享记录 ID
     * @param expireTime 过期时间
     */
    private void addToDelayedQueue(Long id, LocalDateTime expireTime) {
        shareQueueRedis.addToDelayedQueue(id, expireTime);
    }
}
