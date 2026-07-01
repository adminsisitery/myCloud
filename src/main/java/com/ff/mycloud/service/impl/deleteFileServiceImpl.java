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
     * 批量删除文件 → 回收站
     */
    @Override
    public void batchDeleteFile(List<Long> ids) {
        List<FileInfo> validFiles = validateOwnership(ids);

        for (FileInfo file : validFiles) {
            doSoftDelete(file);
        }

        long totalSize = validFiles.stream()
                .filter(f -> !f.getIsFolder())
                .mapToLong(FileInfo::getFileSize)
                .sum();
        updateUserSpace(totalSize);
    }

    /**
     * 删除单文件 → 回收站
     */
    @Override
    public void deleteSingleFile(Long fileId) {
        if (fileId == null) {
            log.error("删除文件失败：fileId为空");
            throw new IllegalArgumentException("文件ID不能为空");
        }

        FileInfo root = fileInfoMapperService.getById(fileId);
        if (root == null) {
            log.error("删除文件失败：未找到ID为{}的文件/文件夹", fileId);
            throw new RuntimeException("文件/文件夹不存在");
        }

        Long currentUserId = UserContext.getCurrentUserId();
        if (!root.getUserId().equals(currentUserId)) {
            log.error("删除文件失败：用户{}无权限删除用户{}的文件(ID:{})",
                    currentUserId, root.getUserId(), fileId);
            throw new RuntimeException("无权限删除该文件/文件夹");
        }

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

        for (FileInfo file : filesToDelete) {
            log.debug("处理：ID={}, 路径={}, 类型={}",
                    file.getId(), file.getLocalFilePath(), file.getIsFolder() ? "文件夹" : "文件");

            if (!file.getIsFolder()) {
                totalSize += file.getFileSize();
            }

            doSoftDelete(file);
        }

        updateUserSpace(totalSize);
    }

    /**
     * 彻底删除（物理删除），清理 OSS 和数据库记录
     */
    @Override
    public void tureDeleteFile(List<Long> fileIds) {
        Long currentUserId = UserContext.getCurrentUserId();

        for (Long id : fileIds) {
            FileInfo file = fileInfoMapperService.getById(id);
            if (file == null) {
                log.warn("物理删除跳过：文件ID={}不存在", id);
                continue;
            }

            // 权限校验（回收站中的文件 isDeleted=true，不能用 findByIdAndUserId）
            if (!file.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }

            long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
            log.debug("文件{}引用数：{}", file.getLocalFilePath(), referenceCount);

            if (referenceCount <= 1) {
                StorageService storageService = storageServiceFactory.getStorageService();
                try {
                    storageService.deleteFile(file.getPath());
                    log.info("物理删除成功：{}", file.getPath());
                } catch (Exception e) {
                    log.error("物理删除失败：{}", file.getPath(), e);
                }
            }

            fileInfoMapperService.removeById(id);
        }
    }

    /**
     * 获取回收站列表
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

            if (file.getDeletedAt() != null) {
                long elapsed = now - file.getDeletedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long remaining = thirtyDaysMs - elapsed;
                vo.setRemainingDays(Math.max(0, (remaining + 24 * 60 * 60 * 1000 - 1)
                        / (24 * 60 * 60 * 1000)));
            }
            return vo;
        }).toList();
    }

    /**
     * 恢复回收站中的文件/文件夹
     * <ol>
     *   <li>校验文件存在 + 属于当前用户 + 确在回收站中</li>
     *   <li>处理同名冲突（目标位置已有同名文件则自动加后缀）</li>
     *   <li>文件夹递归恢复所有子节点</li>
     *   <li>恢复 ES 索引 + 扣回已用空间</li>
     * </ol>
     */
    @Override
    public void restoreFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();

        // 1. 查文件（不过滤 isDeleted，因为文件在回收站里）
        FileInfo file = fileInfoMapperService.getById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!file.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!Boolean.TRUE.equals(file.getIsDeleted())) {
            throw new BusinessException(ErrorCode.FILE_NOT_IN_TRASH);
        }

        // 2. 原父目录已被删除则恢复到根目录
        if (file.getParentId() != null && file.getParentId() != 0) {
            FileInfo parent = fileInfoMapperService.getById(file.getParentId());
            if (parent != null && Boolean.TRUE.equals(parent.getIsDeleted())) {
                log.info("原父目录已删除，恢复到根目录：fileId={}, oldParentId={}",
                        fileId, file.getParentId());
                file.setParentId(0L);
                if (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty()) {
                    file.setLocalFilePath(file.getFilename() + "." + file.getFileSuffix());
                } else {
                    file.setLocalFilePath(file.getFilename());
                }
                fileInfoMapperService.updateById(file);
            }
        }

        // 3. 处理文件名冲突：目标位置已有同名文件则自动重命名
        resolveNameConflict(file, currentUserId);

        // 3. 文件夹：递归恢复所有子节点（用回收站专用查询，因为 isDeleted=true）
        List<FileInfo> filesToRestore;
        if (file.getIsFolder()) {
            String folderPath = file.getLocalFilePath();
            filesToRestore = fileInfoMapperService.getTrashSubTree(folderPath, currentUserId);
        } else {
            filesToRestore = Collections.singletonList(file);
        }

        long restoredSize = 0;
        for (FileInfo f : filesToRestore) {
            f.setIsDeleted(false);
            f.setDeletedAt(null);
            f.setUpdatedAt(LocalDateTime.now());
            fileInfoMapperService.updateById(f);

            if (!f.getIsFolder()) {
                restoredSize += f.getFileSize();
            }

            // 恢复 ES 索引
            FileDocument fd = new FileDocument();
            fd.setId(f.getId());
            fd.setFileName(f.getIsFolder() ? f.getFilename()
                    : f.getFilename() + "." + f.getFileSuffix());
            fd.setUserId(f.getUserId());
            rabbitMqService.sendInsertMessage(fd);
        }

        // 4. 加回已用空间
        addUsedSpace(currentUserId, restoredSize);

        log.info("恢复完成：fileId={}, 总条目={}, 恢复空间={}字节", fileId, filesToRestore.size(), restoredSize);
    }

    /**
     * 清空当前用户的回收站
     */
    @Override
    public void cleanTrash() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> trashFiles = fileInfoMapperService.getTrashFile(currentUserId);

        if (trashFiles.isEmpty()) {
            return;
        }

        log.info("开始清空回收站：用户{}，共{}个文件/文件夹", currentUserId, trashFiles.size());

        for (FileInfo file : trashFiles) {
            long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());

            if (referenceCount <= 1) {
                StorageService storageService = storageServiceFactory.getStorageService();
                try {
                    storageService.deleteFile(file.getPath());
                    log.debug("物理删除成功：{}", file.getPath());
                } catch (Exception e) {
                    log.error("物理删除失败：{}", file.getPath(), e);
                }
            }

            fileInfoMapperService.removeById(file.getId());
        }

        log.info("清空回收站完成：用户{}，共清理{}条记录", currentUserId, trashFiles.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 批量校验文件是否属于当前用户
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
     * 处理恢复时的同名冲突
     * <p>如果原始父目录下已存在同名文件（非回收站中的），自动在文件名后加"(已恢复)"。</p>
     */
    private void resolveNameConflict(FileInfo file, Long currentUserId) {
        String targetName = file.getIsFolder() ? file.getFilename()
                : file.getFilename() + "." + file.getFileSuffix();

        // 查询原始父目录下是否有同名文件（isDeleted=false）
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, file.getParentId());
        boolean conflict = siblings.stream()
                .anyMatch(s -> targetName.equals(
                        s.getIsFolder() ? s.getFilename()
                                : s.getFilename() + "." + s.getFileSuffix()));

        if (conflict) {
            String newName = file.getFilename() + "(已恢复)";
            file.setFilename(newName);
            if (!file.getIsFolder()) {
                // 更新 localFilePath
                String oldPath = file.getLocalFilePath();
                String suffix = file.getFileSuffix() != null ? "." + file.getFileSuffix() : "";
                String parentPath = oldPath != null && oldPath.contains("/")
                        ? oldPath.substring(0, oldPath.lastIndexOf("/") + 1)
                        : "";
                file.setLocalFilePath(parentPath + newName + suffix);
            }
            fileInfoMapperService.updateById(file);
            log.info("同名冲突，自动重命名：{} -> {}", targetName, newName);
        }
    }

    /**
     * 软删除单个文件
     */
    private void doSoftDelete(FileInfo file) {
        file.setIsDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);

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
     * 扣减用户已用空间
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
     * 增加用户已用空间（恢复时）
     */
    private void addUsedSpace(Long userId, long size) {
        UserCenter user = userCenterMapperService.getByUserId(userId);
        if (user == null) {
            return;
        }
        user.setUsedSpace(user.getUsedSpace() + size);
        userCenterMapperService.updateById(user);
    }

    /**
     * 异步发送操作日志
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
