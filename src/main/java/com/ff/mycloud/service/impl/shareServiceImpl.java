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
 * 文件分享服务实现类
 * <p>
 * 实现文件分享的全流程：创建分享（生成ShareCode + 保存记录 + 添加到Redis延迟队列）、
 * 密码/Token验证、取消分享（清理Redis队列和Token）、过期标记等。
 * 分享支持有密码（Token机制）和无密码两种模式。
 * </p>
 *
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


    /**
     * 构造函数，通过Spring进行依赖注入
     *
     * @param fileInfoMapperService  文件信息数据库服务
     * @param shareMapper            分享数据转换器
     * @param shareRecordMapperService 分享记录数据库服务
     * @param shareQueueRedis        分享过期延迟队列Redis操作
     * @param userContext            用户上下文工具
     * @param shareTokenRedis        分享Token Redis操作
     * @param fileService            文件服务（用于下载）
     * @param fileServiceImpl        文件服务实现（用于获取文件内容）
     */
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

    /**
     * 取消分享链接
     * <p>
     * 根据分享码和当前用户ID查找分享记录，验证权限后执行取消操作：
     * 1. 从Redis延迟队列中移除该记录
     * 2. 删除数据库中的分享记录
     * 3. 删除Redis中的分享Token
     * </p>
     *
     * @param shareCode 分享码
     * @throws BusinessException 当分享记录不存在时抛出SHARE_NOT_FOUND异常
     */
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
        log.info("取消分享完成: userId={}, shareId={}", currentUserId, id);
    }

    /**
     * 创建文件分享
     * <p>
     * 流程：校验文件存在性和权限 -> 生成唯一分享码 -> 保存分享记录到数据库 ->
     * 将记录ID加入Redis延迟队列用于到期自动失效。
     * </p>
     *
     * @param fileId     待分享的文件ID
     * @param expireTime 分享过期时间
     * @param password   访问密码（null或空字符串表示无密码）
     * @return 分享文件VO，包含分享码等完整信息
     * @throws BusinessException 当文件不存在时抛出FILE_NOT_FOUND，当无权限时抛出NO_SHARE_PERMISSION
     */
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

        log.info("创建分享完成: userId={}, fileId={}, shareId={}", fileInfo.getUserId(), fileId, shareRecord.getId());
        return shareMapper.toShareFileVO(shareRecord,fileInfo.getFilename(),fileInfo.getFileSize(),fileId, fileInfo.getIsFolder());
    }

    /**
     * 获取当前用户所有未过期的分享文件列表
     * <p>
     * 遍历分享记录关联查询对应的文件信息，文件夹大小为0，
     * 普通文件拼接文件名和后缀返回完整文件名。
     * </p>
     *
     * @return 分享文件VO列表
     */
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

    /**
     * 将分享记录ID添加到Redis延迟队列，用于到期自动过期处理
     *
     * @param id         分享记录ID
     * @param expireTime 过期时间
     */
    private void addToDelayedQueue(Long id, LocalDateTime expireTime) {
        shareQueueRedis.addToDelayedQueue(id,expireTime);
    }

    /**
     * 生成唯一分享码
     * <p>
     * 使用UUID去除横线生成32位字符串作为分享码。
     * </p>
     *
     * @return 32位唯一分享码字符串
     */
    private String makeShareCode() {
       return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 验证分享Token的有效性
     * <p>
     * 无密码分享直接返回true；有密码分享通过Redis验证Token。
     * 分享不存在时返回false而非抛异常，便于调用方做降级处理。
     * </p>
     *
     * @param shareCode 分享码
     * @param token     验证令牌
     * @return true表示Token有效或无需验证，false表示验证失败
     */
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

    /**
     * 通过Token访问分享文件
     * <p>
     * 先验证Token有效性，再查询分享记录和文件信息。
     * 适用于有密码分享的场景，前端已获取Token后携带Token访问。
     * </p>
     *
     * @param shareCode 分享码
     * @param token     访问令牌
     * @return 分享文件VO，包含文件详情
     * @throws BusinessException Token无效时抛出INVALID_TOKEN，分享/文件不存在时抛出对应异常
     */
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

    /**
     * 通过密码访问分享文件
     * <p>
     * 验证分享记录是否存在、是否过期、密码是否匹配，同时检查文件是否未删除。
     * 访问成功后增加一次访问计数。
     * </p>
     *
     * @param shareCode 分享码
     * @param password  明文密码（null或空字符串表示无密码分享）
     * @return 分享文件VO
     * @throws BusinessException 当分享不存在/已过期/密码错误/文件不存在时抛出对应异常
     */
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

    /**
     * 为分享链接生成访问Token
     * <p>
     * 验证分享记录存在且密码正确后，通过Redis生成并存储访问Token。
     * 无密码分享也可通过此方法生成Token。
     * </p>
     *
     * @param shareCode 分享码
     * @param password  明文密码
     * @return 生成的Token字符串
     * @throws BusinessException 当分享不存在或密码错误时抛出异常
     */
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
    /**
     * 下载分享的文件内容
     * <p>
     * 校验分享记录存在且未过期、Token有效（有密码分享）、文件存在且未删除后，
     * 通过文件服务获取文件二进制内容。
     * </p>
     *
     * @param shareCode 分享码
     * @param token     访问令牌（有密码分享需要，无密码可为空）
     * @return 文件的完整字节数组
     * @throws BusinessException 当任何校验不通过时抛出对应异常
     */
    public byte[] downloadSharedFile(String shareCode, String token) {
        log.debug("开始校验分享下载权限");

        ShareRecord shareRecord = shareRecordMapperService.findByShareCode(shareCode);
        if (shareRecord == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }

        // 校验过期
        if (shareRecord.getIsExpired() || shareRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED);
        }

        String password = shareRecord.getPassword();
        log.debug("分享下载是否需要提取码: required={}", password != null && !password.isEmpty());
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

    /**
     * 根据分享码获取对应的完整文件名（含后缀）
     *
     * @param shareCode 分享码
     * @return 完整文件名（文件名+后缀），如 "文档.docx"
     * @throws BusinessException 当分享或文件不存在时抛出异常
     */
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

    /**
     * 批量标记所有已过期的分享为失效状态
     * <p>
     * 查询所有过期时间早于当前时间且尚未标记为过期的记录，统一更新为已过期。
     * 通常由定时任务调用。
     * </p>
     */
    public void markExpiredShares() {
        shareRecordMapperService.lambdaUpdate()
                .lt(ShareRecord::getExpireTime, LocalDateTime.now())
                .eq(ShareRecord::getIsExpired, false)
                .set(ShareRecord::getIsExpired, true)
                .update();
    }
    /**
     * 单条标记指定ID的分享为失效（延迟队列消费者用）
     * @param shareId 分享记录ID
     * @return 是否更新成功（true=成功，false=失败/无匹配记录）
     */
    public boolean markShareExpired(Long shareId) {
        try {
            // 执行单条精准更新：仅更新指定ID、未过期、且已到过期时间的分享
            return shareRecordMapperService.lambdaUpdate()
                    // 核心：仅处理传入的这个分享ID
                    .eq(ShareRecord::getId, shareId)
                    // 条件1：过期时间早于当前时间
                    .lt(ShareRecord::getExpireTime, LocalDateTime.now())
                    // 条件2：未标记为过期
                    .eq(ShareRecord::getIsExpired, false)
                    // 更新：标记为已过期
                    .set(ShareRecord::getIsExpired, true)
                    .update();

            // updateCount > 0 说明有记录被更新，返回成功

        } catch (Exception e) {
            // 记录异常日志，方便排查问题
            log.error("标记分享ID: {} 过期失败", shareId, e);
            // 异常时返回失败
            return false;
        }
    }

}

