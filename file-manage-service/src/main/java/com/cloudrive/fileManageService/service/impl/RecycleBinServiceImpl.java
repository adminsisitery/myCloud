package com.cloudrive.fileManageService.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cloudrive.fileManageService.client.UserSpaceClient;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.model.vo.TrashListVO;
import com.cloudrive.fileManageService.mq.MqProducer;
import com.cloudrive.fileManageService.repositorys.FileObjectMapper;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.RecycleBinService;
import com.cloudrive.fileManageService.service.StorageService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.domain.mq.PgIndexMessage;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.ExceptionUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 回收站服务实现类
 * <p>
 * 从原单体 {@code deleteFileServiceImpl} 迁移软删除/批量删除/彻底删除/
 * 回收站列表/恢复/清空逻辑；空间配额改为调用 user-service 内部接口。
 * MQ/ES/操作日志等跨域副作用不在本服务中执行（仅记日志）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class RecycleBinServiceImpl implements RecycleBinService {

    private static final Logger logger = LoggerFactory.getLogger(RecycleBinServiceImpl.class);

    /** 回收站保留时长（30 天） */
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    /** 一天毫秒数 */
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private final FileInfoMapperService fileInfoMapperService;
    private final FileObjectMapper fileObjectMapper;
    private final UserSpaceClient userSpaceClient;
    private final StorageService storageService;
    private final MqProducer mqProducer;

    public RecycleBinServiceImpl(FileInfoMapperService fileInfoMapperService,
                                 FileObjectMapper fileObjectMapper,
                                 UserSpaceClient userSpaceClient,
                                 StorageService storageService,
                                 MqProducer mqProducer) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileObjectMapper = fileObjectMapper;
        this.userSpaceClient = userSpaceClient;
        this.storageService = storageService;
        this.mqProducer = mqProducer;
    }

    /**
     * 删除单个文件/文件夹（软删除，进入回收站）
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteSingleFile(Long fileId) {
        if (fileId == null) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PARAM, "文件ID不能为空");
        }
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo root = fileInfoMapperService.getById(fileId);
        ExceptionUtil.throwIfNull(root, ErrorCode.FILE_NOT_FOUND);
        if (!Objects.equals(root.getUserId(), currentUserId)) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        List<FileInfo> targets = collectTargets(root, currentUserId);
        long totalSize = 0L;
        LocalDateTime now = LocalDateTime.now();
        for (FileInfo target : targets) {
            if (!Boolean.TRUE.equals(target.getIsFolder()) && target.getFileSize() != null) {
                totalSize += target.getFileSize();
            }
            doSoftDelete(target, now);
        }
        publishSoftDeleteSideEffects(targets);
        releaseSpace(currentUserId, totalSize);
        logger.info("软删除成功: fileId={}, affected={}, releasedBytes={}", fileId, targets.size(), totalSize);
        return Result.success("删除成功");
    }

    /**
     * 批量删除文件/文件夹（软删除，进入回收站）
     *
     * @param ids 逗号分隔的文件 ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> batchDeleteFile(String ids) {
        Long currentUserId = UserContext.getCurrentUserId();
        List<Long> idList = parseIds(ids);
        if (idList.isEmpty()) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PARAM, "文件ID不能为空");
        }
        List<FileInfo> files = fileInfoMapperService.findInIdAndUserId(idList, currentUserId);
        if (files.size() != idList.size()) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        LocalDateTime now = LocalDateTime.now();
        long totalSize = 0L;
        int affected = 0;
        List<FileInfo> allTargets = new ArrayList<>();
        for (FileInfo file : files) {
            List<FileInfo> targets = collectTargets(file, currentUserId);
            allTargets.addAll(targets);
            for (FileInfo target : targets) {
                if (!Boolean.TRUE.equals(target.getIsFolder()) && target.getFileSize() != null) {
                    totalSize += target.getFileSize();
                }
                doSoftDelete(target, now);
                affected++;
            }
        }
        publishSoftDeleteSideEffects(allTargets);
        releaseSpace(currentUserId, totalSize);
        logger.info("批量软删除成功: fileIds={}, affected={}, releasedBytes={}", idList, affected, totalSize);
        return Result.success("删除成功");
    }

    /**
     * 彻底删除文件/文件夹（物理删除 + 释放物理对象）
     *
     * @param fileIds 逗号分隔的文件 ID
     * @return 操作结果
     */
    @Override
    public Result<String> tureDeleteFile(String fileIds) {
        List<Long> idList = parseIds(fileIds);
        if (idList.isEmpty()) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PARAM, "文件ID不能为空");
        }
        Long currentUserId = UserContext.getCurrentUserId();
        for (Long id : idList) {
            FileInfo file = fileInfoMapperService.getById(id);
            if (file == null) {
                continue;
            }
            if (!Objects.equals(file.getUserId(), currentUserId)) {
                ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
            }
            if (!Boolean.TRUE.equals(file.getIsFolder())) {
                deletePhysicalObjectIfUnreferenced(file);
            }
            fileInfoMapperService.removeById(id);
        }
        logger.info("彻底删除成功: fileIds={}", idList);
        return Result.success("彻底删除成功");
    }

    /**
     * 查询回收站列表
     *
     * @return 回收站列表
     */
    @Override
    public List<TrashListVO> getTrashList() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> trashFiles = fileInfoMapperService.getTrashFile(currentUserId);
        LocalDateTime now = LocalDateTime.now();
        List<TrashListVO> result = new ArrayList<>(trashFiles.size());
        for (FileInfo file : trashFiles) {
            TrashListVO vo = new TrashListVO();
            vo.setId(file.getId());
            if (Boolean.TRUE.equals(file.getIsFolder())) {
                vo.setFilename(file.getFilename());
            } else {
                vo.setFilename(file.getFilename() + (file.getFileSuffix() == null || file.getFileSuffix().isEmpty()
                        ? "" : "." + file.getFileSuffix()));
            }
            vo.setFileSize(file.getFileSize());
            vo.setFileType(file.getFileType());
            vo.setIsFolder(file.getIsFolder());
            vo.setFileSuffix(file.getFileSuffix());
            vo.setDeletedAt(file.getDeletedAt());
            if (file.getDeletedAt() == null) {
                vo.setRemainingDays(0L);
            } else {
                long remaining = THIRTY_DAYS_MS - (now.toInstant(java.time.ZoneOffset.UTC)
                        .toEpochMilli() - file.getDeletedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
                vo.setRemainingDays(Math.max((remaining + ONE_DAY_MS - 1) / ONE_DAY_MS, 0L));
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 从回收站恢复文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> restoreFile(Long fileId) {
        FileInfo file = fileInfoMapperService.getById(fileId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);
        Long currentUserId = UserContext.getCurrentUserId();
        if (!Objects.equals(file.getUserId(), currentUserId)) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        if (!Boolean.TRUE.equals(file.getIsDeleted())) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_IN_TRASH);
        }

        String originalPath = file.getLocalFilePath();
        if (file.getParentId() != null && file.getParentId() != 0) {
            FileInfo parent = fileInfoMapperService.getById(file.getParentId());
            if (parent == null || !Objects.equals(parent.getUserId(), currentUserId)
                    || Boolean.TRUE.equals(parent.getIsDeleted())) {
                file.setParentId(0L);
                file.setLocalFilePath(Boolean.TRUE.equals(file.getIsFolder())
                        ? file.getFilename() + "/" : buildFullName(file));
            }
        }

        List<FileInfo> filesToRestore = new ArrayList<>();
        if (Boolean.TRUE.equals(file.getIsFolder()) && originalPath != null) {
            List<FileInfo> subTree = fileInfoMapperService.getTrashSubTree(originalPath, currentUserId);
            for (FileInfo child : subTree) {
                if (!Objects.equals(child.getId(), fileId)) {
                    filesToRestore.add(child);
                }
            }
        }
        filesToRestore.add(0, file);
        resolveNameConflict(file, currentUserId);

        LocalDateTime now = LocalDateTime.now();
        long restoredSize = 0L;
        for (FileInfo target : filesToRestore) {
            if (!Objects.equals(target.getId(), fileId) && originalPath != null) {
                String childPath = target.getLocalFilePath();
                if (childPath != null && childPath.startsWith(originalPath)) {
                    target.setLocalFilePath(file.getLocalFilePath() + childPath.substring(originalPath.length()));
                }
            }
            target.setIsDeleted(false);
            target.setUpdatedAt(now);
            fileInfoMapperService.updateById(target);
            fileInfoMapperService.update(null,
                    new UpdateWrapper<FileInfo>().eq("id", target.getId()).set("deleted_at", null));
            if (!Boolean.TRUE.equals(target.getIsFolder()) && target.getFileSize() != null) {
                restoredSize += target.getFileSize();
            }
        }
        publishRestoreSideEffects(filesToRestore);
        if (restoredSize > 0) {
            userSpaceClient.addUsedSpace(currentUserId, restoredSize);
        }
        logger.info("恢复成功: fileId={}, restored={}, bytes={}", fileId, filesToRestore.size(), restoredSize);
        return Result.success("恢复成功");
    }

    /**
     * 清空回收站
     *
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cleanTrash() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> trashFiles = fileInfoMapperService.getTrashFile(currentUserId);
        for (FileInfo file : trashFiles) {
            if (!Boolean.TRUE.equals(file.getIsFolder())) {
                deletePhysicalObjectIfUnreferenced(file);
            }
            fileInfoMapperService.removeById(file.getId());
        }
        publishCleanTrashSideEffects(trashFiles);
        logger.info("清空回收站完成: userId={}, count={}", currentUserId, trashFiles.size());
        return Result.success("清空回收站成功");
    }

    /** 软删除旁路消息：每个节点投递 ES 删除；普通文件额外投递 PG 删除与操作日志（失败仅 warn）。 */
    private void publishSoftDeleteSideEffects(List<FileInfo> targets) {
        for (FileInfo target : targets) {
            String displayName = buildFullName(target);
            try {
                mqProducer.sendIndexDelete(new FileIndexMessage(target.getId(), displayName, null,
                        target.getUserId(), target.getFileType()));
                if (!Boolean.TRUE.equals(target.getIsFolder())) {
                    mqProducer.sendPgDelete(new PgIndexMessage(target.getId(), target.getUserId(), null,
                            target.getSha256Hash(), false));
                    mqProducer.sendLog(new OperationLogMessage(target.getUserId(), "DELETE", target.getId(),
                            displayName, target.getFileSize(), null, null,
                            UUID.randomUUID().toString(), LocalDateTime.now()));
                }
            } catch (RuntimeException e) {
                logger.warn("软删除旁路消息发送失败（不影响删除结果）: fileId={}", target.getId(), e);
            }
        }
    }

    /** 恢复旁路消息：每个节点投递 ES 插入；普通文件额外投递 PG 重建（失败仅 warn）。 */
    private void publishRestoreSideEffects(List<FileInfo> targets) {
        for (FileInfo target : targets) {
            try {
                mqProducer.sendIndexInsert(new FileIndexMessage(target.getId(), buildFullName(target), null,
                        target.getUserId(), target.getFileType()));
                if (!Boolean.TRUE.equals(target.getIsFolder())) {
                    mqProducer.sendPgInsert(new PgIndexMessage(target.getId(), target.getUserId(), null,
                            target.getSha256Hash(), true));
                }
            } catch (RuntimeException e) {
                logger.warn("恢复旁路消息发送失败（不影响恢复结果）: fileId={}", target.getId(), e);
            }
        }
    }

    /** 清空回收站旁路消息：普通文件投递 PG 向量删除（ES 文档已在软删除时移除，失败仅 warn）。 */
    private void publishCleanTrashSideEffects(List<FileInfo> trashFiles) {
        for (FileInfo file : trashFiles) {
            if (Boolean.TRUE.equals(file.getIsFolder())) {
                continue;
            }
            try {
                mqProducer.sendPgDelete(new PgIndexMessage(file.getId(), file.getUserId(), null,
                        file.getSha256Hash(), false));
            } catch (RuntimeException e) {
                logger.warn("清空回收站旁路消息发送失败（不影响清空结果）: fileId={}", file.getId(), e);
            }
        }
    }

    /**
     * 收集删除目标：文件夹包含整棵子树
     *
     * @param root          根文件/文件夹
     * @param currentUserId 用户 ID
     * @return 待删除文件列表（根在首位）
     */
    private List<FileInfo> collectTargets(FileInfo root, Long currentUserId) {
        List<FileInfo> targets = new ArrayList<>();
        targets.add(root);
        if (Boolean.TRUE.equals(root.getIsFolder()) && root.getLocalFilePath() != null) {
            List<FileInfo> subTree = fileInfoMapperService.getSubTree(root.getLocalFilePath(), currentUserId);
            for (FileInfo child : subTree) {
                if (!Objects.equals(child.getId(), root.getId())) {
                    targets.add(child);
                }
            }
        }
        return targets;
    }

    /**
     * 执行软删除标记
     *
     * @param file 文件
     * @param now  当前时间
     */
    private void doSoftDelete(FileInfo file, LocalDateTime now) {
        file.setIsDeleted(true);
        file.setDeletedAt(now);
        file.setUpdatedAt(now);
        fileInfoMapperService.updateById(file);
    }

    /**
     * 归还用户空间（调用 user-service）
     *
     * @param userId 用户 ID
     * @param size   归还字节数
     */
    private void releaseSpace(Long userId, long size) {
        if (size > 0) {
            userSpaceClient.releaseSpace(userId, size);
        }
    }

    /**
     * 若物理对象无其他引用则从 OSS 删除（失败仅记日志，不阻断数据库删除）
     *
     * @param file 文件
     */
    private void deletePhysicalObjectIfUnreferenced(FileInfo file) {
        if (file.getSha256Hash() == null) {
            return;
        }
        Long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
        if (referenceCount != null && referenceCount <= 1) {
            try {
                storageService.deleteFile(file.getPath());
            } catch (RuntimeException e) {
                logger.warn("删除 OSS 对象失败（忽略，继续删除数据库记录）: fileId={}, path={}",
                        file.getId(), file.getPath(), e);
            }
        }
    }

    /**
     * 恢复前处理同名冲突：同名则重命名为 "原名(已恢复)"
     *
     * @param file   待恢复文件（会就地修改名称与路径并写库）
     * @param userId 用户 ID
     */
    private void resolveNameConflict(FileInfo file, Long userId) {
        boolean isFolder = Boolean.TRUE.equals(file.getIsFolder());
        String name = file.getFilename();
        String suffix = file.getFileSuffix();
        String targetName = isFolder ? name : buildFullName(file);
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(userId, file.getParentId());
        boolean conflict = false;
        for (FileInfo sibling : siblings) {
            String siblingName = Boolean.TRUE.equals(sibling.getIsFolder())
                    ? sibling.getFilename() : buildFullName(sibling);
            if (targetName.equalsIgnoreCase(siblingName)) {
                conflict = true;
                break;
            }
        }
        if (!conflict) {
            return;
        }
        String newName = name + "(已恢复)";
        String oldPath = file.getLocalFilePath();
        file.setFilename(newName);
        if (isFolder) {
            if (oldPath != null && oldPath.endsWith(name + "/")) {
                file.setLocalFilePath(oldPath.substring(0, oldPath.length() - name.length() - 1) + newName + "/");
            }
        } else if (oldPath != null) {
            int slash = oldPath.lastIndexOf('/');
            String parentPath = slash >= 0 ? oldPath.substring(0, slash + 1) : "";
            file.setLocalFilePath(parentPath + newName
                    + (suffix == null || suffix.isEmpty() ? "" : "." + suffix));
        }
        fileInfoMapperService.updateById(file);
        logger.info("恢复时检测到同名冲突，已重命名: fileId={}, newName={}", file.getId(), newName);
    }

    /**
     * 构造带后缀的完整文件名
     *
     * @param file 文件
     * @return 完整文件名
     */
    private String buildFullName(FileInfo file) {
        return file.getFilename() + (file.getFileSuffix() == null || file.getFileSuffix().isEmpty()
                ? "" : "." + file.getFileSuffix());
    }

    /**
     * 解析逗号分隔的文件 ID
     *
     * @param ids 逗号分隔字符串
     * @return ID 列表
     */
    private List<Long> parseIds(String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PARAM, "文件ID格式非法");
            return new ArrayList<>();
        }
    }
}
