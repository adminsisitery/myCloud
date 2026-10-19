package com.cloudrive.aiService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.service.FileInfoMapperService;
import com.cloudrive.aiService.service.FileService;
import com.cloudrive.aiService.service.StorageService;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.ExceptionUtil;
import com.cloudrive.pan_common.util.FileHashUtil;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 文件服务实现（AI Agent 动作）
 * <p>
 * 逻辑照搬单体 FileServiceImpl 的对应方法，去除 MQ/ES 同步与秒传；
 * 配额扣减/归还由调用方（AgentActionService）通过 user-service 内网接口完成，
 * 本实现专注文件元数据与对象存储的一致性。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private static final int MAX_FILENAME_LENGTH = 50;
    private static final String ILLEGAL_FILENAME_CHARS = "\\/:*?\"<>|";

    private final FileInfoMapperService fileInfoMapperService;
    private final StorageService storageService;

    public FileServiceImpl(FileInfoMapperService fileInfoMapperService,
                           StorageService storageService) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.storageService = storageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadFile(MultipartFile file, Long parentId) {
        Long userId = UserContext.getCurrentUserId();
        if (file == null || file.isEmpty()) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PARAM, "文件内容不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "document";
        }
        String suffix = "";
        String filename = originalFilename;
        int dot = originalFilename.lastIndexOf('.');
        if (dot > 0) {
            suffix = originalFilename.substring(dot + 1);
            filename = originalFilename.substring(0, dot);
        }
        Long normalizedParentId = (parentId == null || parentId == 0L) ? 0L : parentId;
        FileInfo parent = null;
        if (normalizedParentId != 0L) {
            parent = fileInfoMapperService.getById(normalizedParentId);
            if (parent == null
                    || !userId.equals(parent.getUserId())
                    || !Boolean.TRUE.equals(parent.getIsFolder())
                    || Boolean.TRUE.equals(parent.getIsDeleted())) {
                ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND, "父目录不存在或无权访问");
            }
        }

        String uploadPath = CommonConstants.File.FILE_PATH_PREFIX + userId;
        String path = storageService.UploadFile(file, uploadPath);
        if (path == null) {
            ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, "文件上传失败");
        }

        String localFilePath = parent == null
                ? filename + (suffix.isEmpty() ? "" : "." + suffix)
                : normalizeDirectory(parent.getLocalFilePath()) + filename + (suffix.isEmpty() ? "" : "." + suffix);

        LocalDateTime now = LocalDateTime.now();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFilename(filename);
        fileInfo.setOriginalFilename(originalFilename);
        fileInfo.setPath(path);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileType(file.getContentType() == null || file.getContentType().isEmpty()
                ? "application/octet-stream" : file.getContentType());
        fileInfo.setSha256Hash(FileHashUtil.calculateSHA256(file));
        fileInfo.setFileSuffix(suffix);
        fileInfo.setUserId(userId);
        fileInfo.setParentId(normalizedParentId == 0L ? null : normalizedParentId);
        fileInfo.setIsFolder(false);
        fileInfo.setIsDeleted(false);
        fileInfo.setLocalFilePath(localFilePath);
        fileInfo.setCreatedAt(now);
        fileInfo.setUpdatedAt(now);
        fileInfo.setIsStarred(false);
        fileInfo.setCategoryId(FileTypeUtil.getCategoryId(suffix));
        fileInfoMapperService.save(fileInfo);

        log.info("Agent 文档保存成功: userId={}, fileId={}, size={}", userId, fileInfo.getId(), file.getSize());
        return fileInfo.getPath();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFile(Long fileId, String newFilename) {
        if (newFilename == null || newFilename.isBlank()) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_FILENAME, "文件名不能为空");
        }
        FileInfo fileInfo = requireOwnedFile(fileId, false);
        String oldName = fileInfo.getFilename();
        String oldPath = fileInfo.getLocalFilePath();
        boolean folder = Boolean.TRUE.equals(fileInfo.getIsFolder());
        String suffix = fileInfo.getFileSuffix();
        String name = newFilename.trim();
        if (!folder && suffix != null && !suffix.isEmpty()
                && name.toLowerCase().endsWith("." + suffix.toLowerCase())) {
            name = name.substring(0, name.length() - suffix.length() - 1);
        }
        validateFilename(name);

        LambdaQueryWrapper<FileInfo> siblingQuery = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, fileInfo.getUserId())
                .eq(FileInfo::getIsDeleted, false);
        Long parentId = fileInfo.getParentId();
        if (parentId == null || parentId == 0L) {
            siblingQuery.isNull(FileInfo::getParentId);
        } else {
            siblingQuery.eq(FileInfo::getParentId, parentId);
        }
        List<FileInfo> siblings = fileInfoMapperService.list(siblingQuery);
        final String finalName = name;
        boolean conflict = siblings.stream().anyMatch(s ->
                !Objects.equals(s.getId(), fileInfo.getId())
                        && finalName.equalsIgnoreCase(s.getFilename())
                        && Objects.equals(folder, Boolean.TRUE.equals(s.getIsFolder()))
                        && Objects.equals(suffix, s.getFileSuffix()));
        if (conflict) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_FILENAME, "目标目录已存在同名文件");
        }

        String parentPath = "";
        if (oldPath != null && !oldPath.isEmpty()) {
            if (folder && oldPath.endsWith(oldName + "/")) {
                parentPath = oldPath.substring(0, oldPath.length() - oldName.length() - 1);
            } else if (oldPath.contains("/")) {
                parentPath = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
            }
        }
        String newPath = parentPath + name + (folder
                ? "/"
                : (suffix == null || suffix.isEmpty() ? "" : "." + suffix));

        fileInfo.setFilename(name);
        fileInfo.setLocalFilePath(newPath);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);

        if (folder && oldPath != null && !oldPath.isEmpty()) {
            List<FileInfo> children = fileInfoMapperService.list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, fileInfo.getUserId())
                    .eq(FileInfo::getIsDeleted, false)
                    .likeRight(FileInfo::getLocalFilePath, oldPath));
            for (FileInfo child : children) {
                if (Objects.equals(child.getId(), fileInfo.getId())) {
                    continue;
                }
                String childPath = child.getLocalFilePath();
                if (childPath != null && childPath.startsWith(oldPath)) {
                    child.setLocalFilePath(newPath + childPath.substring(oldPath.length()));
                    child.setUpdatedAt(LocalDateTime.now());
                    fileInfoMapperService.updateById(child);
                }
            }
        }
        log.info("Agent 重命名文件成功: userId={}, fileId={}, newName={}", fileInfo.getUserId(), fileId, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFile(Long fileId, Long targetParentId) {
        FileInfo source = requireOwnedFile(fileId, false);
        Long normalizedTargetId = (targetParentId == null || targetParentId == 0L) ? 0L : targetParentId;
        String newPathPrefix = "";
        if (normalizedTargetId != 0L) {
            FileInfo target = fileInfoMapperService.getById(normalizedTargetId);
            if (target == null || !Objects.equals(target.getUserId(), source.getUserId())
                    || Boolean.TRUE.equals(target.getIsDeleted())) {
                ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND, "目标文件夹不存在或无权访问");
            }
            if (!Boolean.TRUE.equals(target.getIsFolder())) {
                ExceptionUtil.throwBizException(ErrorCode.FOLDER_NOT_EMPTY, "目标不是文件夹");
            }
            newPathPrefix = normalizeDirectory(target.getLocalFilePath());
            if (Boolean.TRUE.equals(source.getIsFolder())) {
                String sourcePrefix = normalizeDirectory(source.getLocalFilePath());
                if (newPathPrefix.startsWith(sourcePrefix)) {
                    ExceptionUtil.throwBizException(ErrorCode.CANNOT_MOVE_INTO_OWN_SUBTREE);
                }
            }
        }

        String oldPath = source.getLocalFilePath();
        String newPath;
        if (Boolean.TRUE.equals(source.getIsFolder())) {
            newPath = newPathPrefix + source.getFilename() + "/";
        } else {
            String suffix = source.getFileSuffix();
            newPath = newPathPrefix + source.getFilename()
                    + (suffix == null || suffix.isEmpty() ? "" : "." + suffix);
        }
        source.setParentId(normalizedTargetId == 0L ? null : normalizedTargetId);
        source.setLocalFilePath(newPath);
        source.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(source);

        if (Boolean.TRUE.equals(source.getIsFolder()) && oldPath != null && !oldPath.isEmpty()) {
            List<FileInfo> children = fileInfoMapperService.list(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, source.getUserId())
                    .eq(FileInfo::getIsDeleted, false)
                    .likeRight(FileInfo::getLocalFilePath, oldPath));
            for (FileInfo child : children) {
                if (Objects.equals(child.getId(), source.getId())) {
                    continue;
                }
                String childPath = child.getLocalFilePath();
                if (childPath != null && childPath.startsWith(oldPath)) {
                    child.setLocalFilePath(newPath + childPath.substring(oldPath.length()));
                    child.setUpdatedAt(LocalDateTime.now());
                    fileInfoMapperService.updateById(child);
                }
            }
        }
        log.info("Agent 移动文件成功: userId={}, fileId={}, targetParentId={}",
                source.getUserId(), fileId, normalizedTargetId);
    }

    @Override
    public void starFile(Long fileId) {
        FileInfo fileInfo = requireOwnedFile(fileId, false);
        fileInfo.setIsStarred(true);
        fileInfo.setStarredAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);
    }

    @Override
    public void unstarFile(Long fileId) {
        FileInfo fileInfo = requireOwnedFile(fileId, false);
        fileInfo.setIsStarred(false);
        fileInfo.setStarredAt(null);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);
    }

    @Override
    public InputStream getFile(String path) {
        return storageService.getFile(path);
    }

    private FileInfo requireOwnedFile(Long fileId, boolean allowDeleted) {
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!Objects.equals(fileInfo.getUserId(), UserContext.getCurrentUserId())) {
            ExceptionUtil.throwBizException(ErrorCode.NO_PERMISSION);
        }
        if (!allowDeleted && Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileInfo;
    }

    private void validateFilename(String name) {
        if (name.isBlank()
                || name.length() > MAX_FILENAME_LENGTH
                || ".".equals(name)
                || "..".equals(name)) {
            ExceptionUtil.throwBizException(ErrorCode.INVALID_FILENAME);
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || ILLEGAL_FILENAME_CHARS.indexOf(c) >= 0) {
                ExceptionUtil.throwBizException(ErrorCode.INVALID_FILENAME);
            }
        }
    }

    private String normalizeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
