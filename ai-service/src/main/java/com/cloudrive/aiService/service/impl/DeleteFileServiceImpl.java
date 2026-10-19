package com.cloudrive.aiService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cloudrive.aiService.client.UserSpaceClient;
import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.service.DeleteFileService;
import com.cloudrive.aiService.service.FileInfoMapperService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.util.ExceptionUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回收站服务实现（AI Agent 动作）
 * <p>
 * 逻辑照搬单体 DeleteFileServiceImpl 的软删除/恢复路径，去除 MQ/ES/PG/操作日志，
 * 空间释放与归还统一调用 user-service 的 {@code /internal/space/**} 接口。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class DeleteFileServiceImpl implements DeleteFileService {

    private static final Logger log = LoggerFactory.getLogger(DeleteFileServiceImpl.class);

    private final FileInfoMapperService fileInfoMapperService;
    private final UserSpaceClient userSpaceClient;

    public DeleteFileServiceImpl(FileInfoMapperService fileInfoMapperService,
                                 UserSpaceClient userSpaceClient) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.userSpaceClient = userSpaceClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSingleFile(Long fileId) {
        Long userId = UserContext.getCurrentUserId();
        FileInfo root = fileInfoMapperService.getById(fileId);
        if (root == null) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Objects.equals(root.getUserId(), userId)) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        if (Boolean.TRUE.equals(root.getIsDeleted())) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
        }

        List<FileInfo> targets = new ArrayList<>();
        targets.add(root);
        if (Boolean.TRUE.equals(root.getIsFolder())
                && root.getLocalFilePath() != null
                && !root.getLocalFilePath().isEmpty()) {
            List<FileInfo> children = fileInfoMapperService.list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getIsDeleted, false)
                    .likeRight(FileInfo::getLocalFilePath, root.getLocalFilePath()));
            for (FileInfo child : children) {
                if (!Objects.equals(child.getId(), root.getId())) {
                    targets.add(child);
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        long releasedBytes = 0L;
        for (FileInfo target : targets) {
            if (!Boolean.TRUE.equals(target.getIsFolder()) && target.getFileSize() != null) {
                releasedBytes += target.getFileSize();
            }
            target.setIsDeleted(true);
            target.setDeletedAt(now);
            target.setUpdatedAt(now);
            fileInfoMapperService.updateById(target);
        }
        if (releasedBytes > 0) {
            userSpaceClient.releaseSpace(userId, releasedBytes);
        }
        log.info("Agent 删除文件到回收站: userId={}, fileId={}, nodes={}, bytes={}",
                userId, fileId, targets.size(), releasedBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreFile(Long fileId) {
        Long userId = UserContext.getCurrentUserId();
        FileInfo root = fileInfoMapperService.getById(fileId);
        if (root == null) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Objects.equals(root.getUserId(), userId)) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        if (!Boolean.TRUE.equals(root.getIsDeleted())) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_IN_TRASH);
        }

        String originalPath = root.getLocalFilePath();
        Long parentId = root.getParentId();
        if (parentId != null && parentId != 0L) {
            FileInfo parent = fileInfoMapperService.getById(parentId);
            if (parent == null
                    || !Objects.equals(parent.getUserId(), userId)
                    || Boolean.TRUE.equals(parent.getIsDeleted())) {
                root.setParentId(0L);
                root.setLocalFilePath(buildFullName(root));
                root.setUpdatedAt(LocalDateTime.now());
                fileInfoMapperService.updateById(root);
            }
        }

        List<FileInfo> targets = new ArrayList<>();
        targets.add(root);
        if (Boolean.TRUE.equals(root.getIsFolder())
                && originalPath != null
                && !originalPath.isEmpty()) {
            List<FileInfo> children = fileInfoMapperService.list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getIsDeleted, true)
                    .likeRight(FileInfo::getLocalFilePath, originalPath));
            for (FileInfo child : children) {
                if (!Objects.equals(child.getId(), root.getId())) {
                    targets.add(child);
                }
            }
        }

        resolveNameConflict(root, userId);

        LocalDateTime now = LocalDateTime.now();
        long restoredBytes = 0L;
        for (FileInfo target : targets) {
            if (!Objects.equals(target.getId(), root.getId())
                    && originalPath != null
                    && target.getLocalFilePath() != null
                    && target.getLocalFilePath().startsWith(originalPath)) {
                target.setLocalFilePath(root.getLocalFilePath()
                        + target.getLocalFilePath().substring(originalPath.length()));
            }
            target.setIsDeleted(false);
            target.setDeletedAt(null);
            target.setUpdatedAt(now);
            fileInfoMapperService.updateById(target);
            fileInfoMapperService.update(null, new UpdateWrapper<FileInfo>()
                    .eq("id", target.getId())
                    .set("deleted_at", null));
            if (!Boolean.TRUE.equals(target.getIsFolder()) && target.getFileSize() != null) {
                restoredBytes += target.getFileSize();
            }
        }
        if (restoredBytes > 0) {
            userSpaceClient.addUsedSpace(userId, restoredBytes);
        }
        log.info("Agent 恢复回收站文件: userId={}, fileId={}, nodes={}, bytes={}",
                userId, fileId, targets.size(), restoredBytes);
    }

    private void resolveNameConflict(FileInfo file, Long userId) {
        String targetName = buildFullName(file);
        LambdaQueryWrapper<FileInfo> siblingQuery = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false);
        Long parentId = file.getParentId();
        if (parentId == null || parentId == 0L) {
            siblingQuery.isNull(FileInfo::getParentId);
        } else {
            siblingQuery.eq(FileInfo::getParentId, parentId);
        }
        List<FileInfo> siblings = fileInfoMapperService.list(siblingQuery);
        boolean conflict = siblings.stream().anyMatch(s -> targetName.equals(buildFullName(s)));
        if (!conflict) {
            return;
        }

        String oldName = file.getFilename();
        String oldPath = file.getLocalFilePath();
        String newName = oldName + "(已恢复)";
        file.setFilename(newName);
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            String parentPath = "";
            if (oldPath != null && !oldPath.isEmpty()) {
                if (oldPath.endsWith(oldName + "/")) {
                    parentPath = oldPath.substring(0, oldPath.length() - oldName.length() - 1);
                } else if (oldPath.contains("/")) {
                    parentPath = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
                }
            }
            file.setLocalFilePath(parentPath + newName + "/");
        } else {
            String suffix = file.getFileSuffix() == null || file.getFileSuffix().isEmpty()
                    ? "" : "." + file.getFileSuffix();
            String parentPath = "";
            if (oldPath != null && oldPath.contains("/")) {
                parentPath = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
            }
            file.setLocalFilePath(parentPath + newName + suffix);
        }
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);
    }

    private String buildFullName(FileInfo file) {
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            return file.getFilename();
        }
        String suffix = file.getFileSuffix();
        return file.getFilename() + (suffix == null || suffix.isEmpty() ? "" : "." + suffix);
    }
}
