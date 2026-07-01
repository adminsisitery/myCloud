package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.IpUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.OperationLog;
import com.ff.mycloud.model.entity.UserCenter;
import com.ff.mycloud.model.vo.TrashListVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.service.DeleteFileService;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import com.ff.mycloud.service.UserCenterMapperService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static cn.dev33.satoken.SaManager.log;

@Service
public class deleteFileServiceImpl implements DeleteFileService {

    private final FileInfoMapperService fileInfoMapperService;
    private final RabbitMqService rabbitMqService;
    private final UserCenterMapperService userCenterMapperService;
    private final StorageServiceFactory storageServiceFactory;

    public deleteFileServiceImpl(FileInfoMapperService fileInfoMapperService,
                                  RabbitMqService rabbitMqService,
                                  UserCenterMapperService userCenterMapperService,
                                  StorageServiceFactory storageServiceFactory) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.rabbitMqService = rabbitMqService;
        this.userCenterMapperService = userCenterMapperService;
        this.storageServiceFactory = storageServiceFactory;
    }

    /**
     * 批量删除文件 → 回收站（仅校验归属，实际删除逐个调用 deleteSingleFile）
     *
     * @param ids 文件ID列表
     */
    @Override
    public void batchDeleteFile(List<Long> ids) {
        // 1. 批量校验是否属于当前用户
        List<FileInfo> validFiles = validateOwnership(ids);

        // 2. 逐个软删除
        for (FileInfo file : validFiles) {
            doSoftDelete(file);
        }

        // 3. 更新用户空间
        long totalSize = validFiles.stream()
                .filter(f -> !f.getIsFolder())
                .mapToLong(FileInfo::getFileSize)
                .sum();
        updateUserSpace(totalSize);
    }

    /**
     * 删除单文件 → 回收站
     *
     * @param fileId 文件ID
     */
    public void deleteSingleFile(Long fileId) {
        if (fileId == null) {
            log.error("删除文件失败：fileId为空");
            throw new IllegalArgumentException("文件ID不能为空");
        }

        // 1. 获取根节点并校验
        FileInfo root = fileInfoMapperService.getById(fileId);
        if (root == null) {
            log.error("删除文件失败：未找到ID为{}的文件/文件夹", fileId);
            throw new RuntimeException("文件/文件夹不存在");
        }

        // 2. 权限校验
        Long currentUserId = UserContext.getCurrentUserId();
        if (!root.getUserId().equals(currentUserId)) {
            log.error("删除文件失败：用户{}无权限删除用户{}的文件(ID:{})",
                    currentUserId, root.getUserId(), fileId);
            throw new RuntimeException("无权限删除该文件/文件夹");
        }

        // 3. 核心分支：判断是文件还是文件夹
        List<FileInfo> filesToDelete;
        if (root.getIsFolder()) {
            String folderPath = root.getLocalFilePath();
            if (folderPath == null || folderPath.isEmpty()) {
                log.error("删除文件夹失败：路径为空，ID={}", fileId);
                throw new RuntimeException("文件夹路径异常");
            }
            if (folderPath.endsWith("/")) {
                folderPath = folderPath.substring(0, folderPath.length() - 1);
            }
            log.info("开始删除文件夹：ID={}, 路径={}, 用户ID={}", fileId, folderPath, currentUserId);
            filesToDelete = fileInfoMapperService.getSubTree(folderPath, currentUserId);
        } else {
            log.info("开始删除文件：ID={}, 路径={}, 用户ID={}", fileId, root.getLocalFilePath(), currentUserId);
            filesToDelete = Collections.singletonList(root);
        }

        log.info("待处理的文件/文件夹总数：{}", filesToDelete.size());
        long totalSize = 0;

        // 4. 统一处理删除逻辑
        for (FileInfo file : filesToDelete) {
            log.debug("处理：ID={}, 路径={}, 类型={}",
                    file.getId(), file.getLocalFilePath(), file.getIsFolder() ? "文件夹" : "文件");

            if (!file.getIsFolder()) {
                totalSize += file.getFileSize();
            }

            doSoftDelete(file);
        }

        // 5. 更新用户空间
        updateUserSpace(totalSize);
    }
    /**
     * 彻底删除（物理删除），清理 OSS 和数据库记录
     *
     * @param fileIds 文件ID列表
     */
    @Override
    public void tureDeleteFile(List<Long> fileIds) {
        validateOwnership(fileIds);

        for (Long id : fileIds) {
            FileInfo file = fileInfoMapperService.getById(id);
            if (file == null) {
                log.warn("物理删除跳过：文件ID={}不存在", id);
                continue;
            }

            long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
            log.debug("文件{}引用数：{}", file.getLocalFilePath(), referenceCount);

            // 无其他引用时物理删除 OSS 存储
            if (referenceCount <= 1) {
                StorageService storageService = storageServiceFactory.getStorageService();
                try {
                    storageService.deleteFile(file.getPath());
                    log.info("物理删除成功：{}", file.getPath());
                } catch (Exception e) {
                    log.error("物理删除失败：{}", file.getPath(), e);
                }
            }

            // 删除数据库记录
            fileInfoMapperService.removeById(id);
        }
    }

    /**
     * 获取回收站列表
     *
     * @return 回收站文件VO列表
     */
    @Override
    public List<TrashListVO> getTrashList() {
        Long currentUserId = UserContext.getCurrentUserId();

        List<FileInfo> trashFiles = fileInfoMapperService.getTrashFile(currentUserId);
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;

        return trashFiles.stream().map(file -> {
            TrashListVO vo = new TrashListVO();
            vo.setId(file.getId());
            vo.setFilename(file.getIsFolder() ? file.getFilename()
                    : file.getFilename() + "." + file.getFileSuffix());
            vo.setFileSize(file.getFileSize());
            vo.setFileType(file.getFileType());
            vo.setIsFolder(file.getIsFolder());
            vo.setFileSuffix(file.getFileSuffix());
            vo.setDeletedAt(file.getDeletedAt());

            // 计算剩余天数
            if (file.getDeletedAt() != null) {
                long elapsed = now - file.getDeletedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long remaining = thirtyDaysMs - elapsed;
                vo.setRemainingDays(Math.max(0, (remaining + 24 * 60 * 60 * 1000 - 1)
                        / (24 * 60 * 60 * 1000))); // 向上取整天
            }
            return vo;
        }).toList();
    }

    @Override
    public void restoreFile(Long fileId) {

    }


    /**
     * 批量校验文件是否属于当前用户
     *
     * @param ids 文件ID列表
     * @return 校验通过的文件列表
     * @throws BusinessException 存在不合法ID时抛出
     */
    private List<FileInfo> validateOwnership(List<Long> ids) {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> files = fileInfoMapperService.findInIdAndUserId(ids, currentUserId);
        if (files.size() != ids.size()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return files;
    }

    /**
     * 软删除单个文件（标记 is_deleted，刷新时间，异步同步 ES）
     *
     * @param file 文件信息
     */
    private void doSoftDelete(FileInfo file) {
        file.setIsDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);

        // 异步同步 ES 删除 + 操作日志
        FileDocument fd = new FileDocument();
        fd.setId(file.getId());
        rabbitMqService.sendDeleteMessage(fd);

        if (!file.getIsFolder()) {
            String name = file.getFilename();
            if (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty()) {
                name = name + "." + file.getFileSuffix();
            }
            sendOpLog(OperationLog.OP_DELETE, file.getId(), name, file.getFileSize(), null);
        }
    }

    /**
     * 更新用户已用空间（减去释放的大小）
     *
     * @param totalSize 释放的总大小（字节）
     */
    private void updateUserSpace(long totalSize) {
        Long currentUserId = UserContext.getCurrentUserId();
        UserCenter user = userCenterMapperService.getByUserId(currentUserId);
        if (user == null) {
            log.error("更新用户空间失败：未找到用户{}", currentUserId);
            throw new RuntimeException("用户不存在");
        }
        long newUsedSpace = Math.max(0, user.getUsedSpace() - totalSize);
        user.setUsedSpace(newUsedSpace);
        userCenterMapperService.updateById(user);
        log.info("删除完成：共释放空间{}字节，用户{}当前已用空间{}",
                totalSize, currentUserId, newUsedSpace);
    }

    /**
     * 异步发送操作日志（通过MQ）
     *
     * @param operation 操作类型
     * @param fileId    文件ID
     * @param filename  文件名
     * @param fileSize  文件大小
     * @param extraInfo 额外信息
     */
    private void sendOpLog(String operation, Long fileId, String filename, Long fileSize, String extraInfo) {
        OperationLog log = new OperationLog();
        log.setUserId(UserContext.getCurrentUserId());
        log.setOperation(operation);
        log.setFileId(fileId);
        log.setFilename(filename);
        log.setFileSize(fileSize);
        log.setExtraInfo(extraInfo);
        log.setClientIp(IpUtil.getClientIp());
        log.setCreateTime(LocalDateTime.now());
        rabbitMqService.sendLogMessage(log);
    }
}
