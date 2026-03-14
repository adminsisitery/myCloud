package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.ShareMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.ShareFileVO;
import com.ff.mycloud.redis.ShareTokenRedis;
import com.ff.mycloud.redis.shareQueueRedis;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.ShareRecordMapperServic;
import com.ff.mycloud.service.shareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/5 14:18
 * @version 1.0
 */

@Slf4j
@Service
public class shareServiceImpl implements shareService {

    private final FileServiceImpl fileServiceImpl;
    FileInfoMapperService fileInfoMapperService;
    ShareRecordMapperServic shareRecordMapperService;
    ShareMapper shareMapper;
    shareQueueRedis shareQueueRedis;
    UserContext userContext;
    ShareTokenRedis shareTokenRedis;
    FileService fileService;


    public shareServiceImpl(FileInfoMapperService fileInfoMapperService,
                            ShareMapper shareMapper,
                            ShareRecordMapperServic shareRecordMapperService,
                            shareQueueRedis shareQueueRedis,
                            UserContext userContext,
                            ShareTokenRedis shareTokenRedis,
                            FileService fileService, FileServiceImpl fileServiceImpl) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.shareMapper = shareMapper;
        this.shareRecordMapperService = shareRecordMapperService;
        this.shareQueueRedis = shareQueueRedis;
        this.userContext = userContext;
        this.shareTokenRedis = shareTokenRedis;
        this.fileService = fileService;
        this.fileServiceImpl = fileServiceImpl;
    }

    @Override
    public void cancelShare(String shareCode) {
        Long currentUserId = UserContext.getCurrentUserId();
        ShareRecord byUserIdAndShareCode = shareRecordMapperService.findByUserIdAndShareCode(currentUserId, shareCode);
        if (byUserIdAndShareCode==null){
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        Long id = byUserIdAndShareCode.getId();

        shareQueueRedis.removeFromDelayedQueue(id);

        shareRecordMapperService.removeById(id);

        // 删除token
        shareTokenRedis.deleteToken(shareCode);
    }

    @Override
    public ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password) {
        if (password != null && password.trim().isEmpty()) {
            password = null;
        }
        //获取当前用户
        User currentUser = UserContext.getCurrentUser();
        //获取文件信息
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        //验证文件权限
        ExceptionUtil.throwIf(
                !fileInfo.getUserId().equals(currentUser.getId()),
                ErrorCode.NO_SHARE_PERMISSION
        );

        //生成分享码
        String shareCode = makeShareCode();
        //创建分享记录
        ShareRecord shareRecord = shareMapper.toShareRecord(fileInfo.getId(), currentUser.getId(), shareCode, password, expireTime,LocalDateTime.now());
        shareRecordMapperService.save(shareRecord);
        //添加到延迟队列redis
        addToDelayedQueue(shareRecord.getId(),expireTime);

        return shareMapper.toShareFileVO(shareRecord,fileInfo.getFilename(),fileInfo.getFileSize(),fileId, fileInfo.getIsFolder());
    }

    @Override
    public List<ShareFileVO> getSharedFiles() {

        Long userId = UserContext.getCurrentUserId();

        List<ShareRecord> records =
                shareRecordMapperService.findByUserIdAndNotDel(userId);

       return records.stream()
                .map(r -> {
                    FileInfo file = fileInfoMapperService.getById(r.getFileId());
                    if (file.getIsFolder()){
                        return shareMapper.toShareFileVO(r,file.getFilename(),0L,file.getId(),file.getIsFolder());
                    }
                    return shareMapper.toShareFileVO(
                            r,
                            file.getFilename()+"."+file.getFileSuffix(),
                            file.getFileSize(),
                            file.getId(),
                            file.getIsFolder());
                })
                .toList();

    }

    private void addToDelayedQueue(Long id, LocalDateTime expireTime) {
        shareQueueRedis.addToDelayedQueue(id,expireTime);
    }

    private String makeShareCode() {
       return UUID.randomUUID().toString().replace("-", "");
    }

    public boolean validateShareToken(String shareCode, String token) {

        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);

        if (shareRecord == null) {
            return false; // 改这里！不要抛异常，返回false即可
        }

        String password = shareRecord.getPassword();

        // 无密码分享
        if (password == null || password.trim().isEmpty()) {
            return true;
        }

        return shareTokenRedis.validateToken(shareCode, token);
    }

    public ShareFileVO accessShareByToken(String shareCode, String token) {
        ExceptionUtil.throwIf(
                !validateShareToken(shareCode,token),
                ErrorCode.INVALID_TOKEN
        );

        ShareRecord byShareCode = shareRecordMapperService.findByShareCode(shareCode);
        if (byShareCode == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        Long fileId = byShareCode.getFileId();
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null|| fileInfo.getIsDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return shareMapper.toShareFileVO(byShareCode,fileInfo.getFilename()+"."+fileInfo.getFileSuffix(),fileInfo.getFileSize(),fileId, fileInfo.getIsFolder());
    }

    public ShareFileVO accessShare(String shareCode, String password) {
        // 获取分享记录
        ShareRecord byShareCode = shareRecordMapperService.findByShareCode(shareCode);
        if (byShareCode == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        // 检查是否过期
        ExceptionUtil.throwIf(
                byShareCode.getIsExpired()||byShareCode.getExpireTime().isBefore(LocalDateTime.now()),
                ErrorCode.SHARE_EXPIRED
        );
        // 验证密码
        if (byShareCode.getPassword()!=null && !byShareCode.getPassword().isEmpty()) {
            ExceptionUtil.throwIf(password==null,ErrorCode.MISSING_PASSWORD);
            ExceptionUtil.throwIf(!password.equals(byShareCode.getPassword()),ErrorCode.INVALID_PASSWORD);
        }

        // 检查文件是否存在且未被删除
        Long fileId = byShareCode.getFileId();
        FileInfo byId = fileInfoMapperService.getById(fileId);
        if (byId == null||byId.getIsDeleted()) {
           throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        // 更新访问次数
        shareRecordMapperService.visitedPlus1(byShareCode.getVisitCount(),byShareCode.getId());
        // 返回分享信息
        return shareMapper.toShareFileVO(byShareCode,byId.getFilename()+"."+byId.getFileSuffix(),byId.getFileSize(),fileId, byId.getIsFolder());
    }

    public String generateShareToken(String shareCode, String password) {
        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);
        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        if (shareRecord.getPassword()!=null && !shareRecord.getPassword().isEmpty()) {
            ExceptionUtil.throwIf(password==null,ErrorCode.INVALID_PASSWORD);
        }
        return shareTokenRedis.generateAndStoreToken(shareCode);
    }
    public byte[] downloadSharedFile(String shareCode, String token) {
        log.debug("downloadSharedFile shareCode={}, token={}", shareCode, token);

        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);
        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        // 校验过期
        if (shareRecord.getIsExpired() || shareRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        String password = shareRecord.getPassword();
        log.debug("password='{}'", password);
        if (password != null && !password.trim().isEmpty()) {

            if (!validateShareToken(shareCode, token)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }

        }
        FileInfo fileInfo = fileInfoMapperService.getById(shareRecord.getFileId());
        if (fileInfo == null || fileInfo.getIsDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return fileService.getFileContent(fileInfo.getId());
    }

    public String getFilename(String shareCode) {
        ShareRecord byShareCode = shareRecordMapperService.findByShareCode(shareCode);
        if (byShareCode == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        FileInfo byId = fileInfoMapperService.getById(byShareCode.getFileId());
        if (byId == null||byId.getIsDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return byId.getFilename()+"."+byId.getFileSuffix();
    }

    public void markExpiredShares() {
        shareRecordMapperService.lambdaUpdate()
                .lt(ShareRecord::getExpireTime, LocalDateTime.now())
                .eq(ShareRecord::getIsExpired, false)
                .set(ShareRecord::getIsExpired, true)
                .update();
    }
}

