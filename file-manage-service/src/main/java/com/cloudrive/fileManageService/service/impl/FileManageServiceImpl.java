package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.mapper.FileMapper;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.model.entity.FileObject;
import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.fileManageService.mq.MqProducer;
import com.cloudrive.fileManageService.repositorys.FileObjectMapper;
import com.cloudrive.fileManageService.repositorys.FileRecentAccess;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.FileManageService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import com.cloudrive.pan_common.domain.mq.OperationLogMessage;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.enums.FileObjectStatus;
import com.cloudrive.pan_common.result.Result;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件与文件夹管理服务实现类
 * <p>
 * 从原单体 {@code FileServiceImpl} 中迁移列表、重命名、移动、复制、收藏、
 * 最近访问等元数据管理逻辑；上传/下载/秒传/ES 搜索不在本服务范围内。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FileManageServiceImpl implements FileManageService {

    private static final Logger logger = LoggerFactory.getLogger(FileManageServiceImpl.class);

    /** 最近访问返回条数 */
    private static final int RECENT_FILE_LIMIT = 10;

    /** 文件名最大长度 */
    private static final int MAX_FILENAME_LENGTH = 50;

    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;
    private final FileObjectMapper fileObjectMapper;
    private final FileRecentAccess fileRecentAccess;
    private final MqProducer mqProducer;

    public FileManageServiceImpl(FileInfoMapperService fileInfoMapperService,
                                 FileMapper fileMapper,
                                 FileObjectMapper fileObjectMapper,
                                 FileRecentAccess fileRecentAccess,
                                 MqProducer mqProducer) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.fileRecentAccess = fileRecentAccess;
        this.mqProducer = mqProducer;
    }

    /**
     * 查询指定父目录下的文件/文件夹列表
     *
     * @param parentId 父目录 ID（0 或 null 表示根目录）
     * @return 文件列表
     */
    @Override
    public List<FileListVO> listFiles(Long parentId) {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> fileInfos = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        List<FileListVO> result = new ArrayList<>(fileInfos.size());
        for (FileInfo fileInfo : fileInfos) {
            appendSuffixIfFile(fileInfo);
            result.add(fileMapper.toFileListVO(fileInfo));
        }
        return result;
    }

    /**
     * 查询最近访问的文件列表
     *
     * @return 最近访问列表
     */
    @Override
    public List<FileListVO> getRecentFiles() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> recentFiles = fileRecentAccess.getRecentFiles(currentUserId, RECENT_FILE_LIMIT);
        List<FileListVO> result = new ArrayList<>(recentFiles.size());
        for (FileInfo fileInfo : recentFiles) {
            appendSuffixIfFile(fileInfo);
            result.add(fileMapper.toFileListVO(fileInfo));
        }
        return result;
    }

    /**
     * 重命名文件/文件夹
     *
     * @param fileId      文件 ID
     * @param newFilename 新名称
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> renameFile(Long fileId, String newFilename) {
        if (newFilename == null || newFilename.trim().isEmpty()) {
            return Result.error("新文件名不能为空");
        }
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            return Result.error("文件不存在");
        }
        Long currentUserId = UserContext.getCurrentUserId();
        if (!Objects.equals(fileInfo.getUserId(), currentUserId)
                || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            return Result.error("无权操作该文件");
        }

        String filename = newFilename.trim();
        boolean isFolder = Boolean.TRUE.equals(fileInfo.getIsFolder());
        String suffix = fileInfo.getFileSuffix();
        if (!isFolder && suffix != null && !suffix.isEmpty()
                && filename.toLowerCase().endsWith("." + suffix.toLowerCase())) {
            filename = filename.substring(0, filename.length() - suffix.length() - 1);
        }
        if (filename.isEmpty() || filename.length() > MAX_FILENAME_LENGTH
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")
                || hasIllegalCharacter(filename)) {
            return Result.error("文件名不合法");
        }

        String oldName = fileInfo.getFilename();
        Long parentId = fileInfo.getParentId();
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        for (FileInfo sibling : siblings) {
            if (Objects.equals(sibling.getId(), fileId)) {
                continue;
            }
            if (filename.equalsIgnoreCase(sibling.getFilename())
                    && Objects.equals(fileInfo.getIsFolder(), sibling.getIsFolder())
                    && Objects.equals(suffix, sibling.getFileSuffix())) {
                return Result.error("目标目录已存在同名文件");
            }
        }

        String oldPath = fileInfo.getLocalFilePath();
        fileInfo.setFilename(filename);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        if (oldPath != null) {
            String parentPath;
            if (isFolder && oldPath.endsWith(oldName + "/")) {
                parentPath = oldPath.substring(0, oldPath.length() - oldName.length() - 1);
            } else {
                parentPath = oldPath.substring(0, oldPath.lastIndexOf('/') + 1);
            }
            String newPath = parentPath + filename
                    + (isFolder ? "/" : (suffix == null || suffix.isEmpty() ? "" : "." + suffix));
            fileInfo.setLocalFilePath(newPath);
            fileInfoMapperService.updateById(fileInfo);
            if (isFolder) {
                List<FileInfo> descendants = fileInfoMapperService.getSubTree(oldPath, currentUserId);
                for (FileInfo child : descendants) {
                    if (Objects.equals(child.getId(), fileId)) {
                        continue;
                    }
                    String childPath = child.getLocalFilePath();
                    if (childPath == null || !childPath.startsWith(oldPath)) {
                        continue;
                    }
                    child.setLocalFilePath(newPath + childPath.substring(oldPath.length()));
                    child.setUpdatedAt(LocalDateTime.now());
                    fileInfoMapperService.updateById(child);
                }
            }
        } else {
            fileInfoMapperService.updateById(fileInfo);
        }
        logger.info("重命名成功: fileId={}, {} -> {}", fileId, oldName, filename);
        publishRenameSideEffects(fileInfo);
        return Result.success("重命名成功");
    }

    /**
     * 移动文件/文件夹到目标目录
     *
     * @param fileId         文件 ID
     * @param targetParentId 目标父目录 ID（0 或 null 表示根目录）
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> moveFile(Long fileId, Long targetParentId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);

        String newPathPrefix;
        if (targetParentId != null && targetParentId != 0) {
            FileInfo targetParent = fileInfoMapperService.findByIdAndUserId(targetParentId, currentUserId);
            ExceptionUtil.throwIfNull(targetParent, ErrorCode.FILE_NOT_FOUND);
            if (!Boolean.TRUE.equals(targetParent.getIsFolder())) {
                ExceptionUtil.throwBizException(ErrorCode.FOLDER_NOT_EMPTY, "目标不是文件夹");
            }
            if (Boolean.TRUE.equals(file.getIsFolder())) {
                String sourcePath = file.getLocalFilePath();
                String targetPath = targetParent.getLocalFilePath();
                if (sourcePath != null && targetPath != null && targetPath.startsWith(sourcePath)) {
                    ExceptionUtil.throwBizException(ErrorCode.CANNOT_MOVE_INTO_OWN_SUBTREE);
                }
            }
            newPathPrefix = normalizeDirectory(targetParent.getLocalFilePath());
        } else {
            targetParentId = null;
            newPathPrefix = "";
        }

        String oldPathPrefix = file.getLocalFilePath();
        String newFilePath = Boolean.TRUE.equals(file.getIsFolder())
                ? newPathPrefix + file.getFilename() + "/"
                : newPathPrefix + buildFullName(file);
        file.setParentId(targetParentId);
        file.setLocalFilePath(newFilePath);
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);

        if (Boolean.TRUE.equals(file.getIsFolder()) && oldPathPrefix != null) {
            List<FileInfo> children = fileInfoMapperService.getSubTree(oldPathPrefix, currentUserId);
            for (FileInfo child : children) {
                if (Objects.equals(child.getId(), fileId)) {
                    continue;
                }
                String childPath = child.getLocalFilePath();
                if (childPath == null || !childPath.startsWith(oldPathPrefix)) {
                    continue;
                }
                child.setLocalFilePath(newFilePath + childPath.substring(oldPathPrefix.length()));
                child.setUpdatedAt(LocalDateTime.now());
                fileInfoMapperService.updateById(child);
            }
        }
        logger.info("移动成功: fileId={} -> targetParentId={}", fileId, targetParentId);
        return Result.success("移动成功");
    }

    /**
     * 复制文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> copyFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo source = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(source, ErrorCode.FILE_NOT_FOUND);
        List<FileInfo> copiedFiles = new ArrayList<>();

        if (Boolean.TRUE.equals(source.getIsFolder())) {
            String oldPrefix = normalizeDirectory(source.getLocalFilePath());
            String oldPrefixNormalized = oldPrefix.endsWith("/")
                    ? oldPrefix.substring(0, oldPrefix.length() - 1) : oldPrefix;
            List<FileInfo> children = fileInfoMapperService.getSubTree(source.getLocalFilePath(), currentUserId);

            String copyName = resolveCopyName(source.getFilename(), null, source.getParentId(), currentUserId, true);
            FileInfo newRoot = cloneFileInfo(source);
            newRoot.setFilename(copyName);
            String newRootPath;
            if (source.getLocalFilePath() == null) {
                newRootPath = copyName + "/";
            } else {
                newRootPath = source.getLocalFilePath().replaceFirst(
                        Pattern.quote(source.getFilename()) + "/?$",
                        Matcher.quoteReplacement(copyName + "/"));
            }
            newRoot.setLocalFilePath(newRootPath);
            fileInfoMapperService.save(newRoot);
            copiedFiles.add(newRoot);
            Long newRootId = newRoot.getId();

            for (FileInfo child : children) {
                if (Objects.equals(child.getId(), source.getId())) {
                    continue;
                }
                FileInfo newChild = cloneFileInfo(child);
                String childPath = child.getLocalFilePath();
                if (childPath != null && childPath.length() >= oldPrefixNormalized.length()) {
                    String relativePart = childPath.substring(oldPrefixNormalized.length());
                    if (relativePart.startsWith("/")) {
                        relativePart = relativePart.substring(1);
                    }
                    newChild.setLocalFilePath(newRootPath + relativePart);
                }
                if (Objects.equals(child.getParentId(), source.getId())) {
                    newChild.setParentId(newRootId);
                }
                fileInfoMapperService.save(newChild);
                copiedFiles.add(newChild);
            }
        } else {
            String copyName = resolveCopyName(source.getFilename(), source.getFileSuffix(),
                    source.getParentId(), currentUserId, false);
            FileInfo copyFile = cloneFileInfo(source);
            copyFile.setFilename(copyName);
            fileInfoMapperService.save(copyFile);
            copiedFiles.add(copyFile);
            FileObject fileObject = fileMapper.toFileObject(copyFile, FileObjectStatus.AVAILABLE);
            fileObjectMapper.createFileObject(fileObject);
        }
        logger.info("复制成功: sourceFileId={}", fileId);
        publishCopySideEffects(copiedFiles);
        return Result.success("复制成功");
    }

    /** 重命名旁路消息：ES 更新 + 操作日志（失败仅记日志，不影响重命名结果）。 */
    private void publishRenameSideEffects(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getId() == null) {
            return;
        }
        String displayName = buildFullName(fileInfo);
        try {
            mqProducer.sendIndexUpdate(new FileIndexMessage(fileInfo.getId(), displayName, null,
                    fileInfo.getUserId(), fileInfo.getFileType()));
            mqProducer.sendLog(new OperationLogMessage(fileInfo.getUserId(), "RENAME", fileInfo.getId(),
                    displayName, fileInfo.getFileSize(), null, null,
                    UUID.randomUUID().toString(), LocalDateTime.now()));
        } catch (RuntimeException e) {
            logger.warn("重命名旁路消息发送失败（不影响重命名结果）: fileId={}", fileInfo.getId(), e);
        }
    }

    /** 复制旁路消息：为新复制的文件投递 ES 插入（物理对象复用，无需重建 PG 向量）。 */
    private void publishCopySideEffects(List<FileInfo> copiedFiles) {
        for (FileInfo copied : copiedFiles) {
            try {
                mqProducer.sendIndexInsert(new FileIndexMessage(copied.getId(), buildFullName(copied), null,
                        copied.getUserId(), copied.getFileType()));
            } catch (RuntimeException e) {
                logger.warn("复制旁路消息发送失败（不影响复制结果）: fileId={}", copied.getId(), e);
            }
        }
    }

    /**
     * 收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @Override
    public Result<String> starFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);
        file.setIsStarred(true);
        file.setStarredAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);
        return Result.success("收藏成功");
    }

    /**
     * 取消收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @Override
    public Result<String> unstarFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);
        file.setIsStarred(false);
        file.setStarredAt(null);
        fileInfoMapperService.updateById(file);
        return Result.success("取消收藏成功");
    }

    /**
     * 查询收藏列表
     *
     * @return 收藏列表
     */
    @Override
    public List<FileListVO> listStarredFiles() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> files = fileInfoMapperService.findStarredFiles(currentUserId);
        List<FileListVO> result = new ArrayList<>(files.size());
        for (FileInfo fileInfo : files) {
            appendSuffixIfFile(fileInfo);
            result.add(fileMapper.toFileListVO(fileInfo));
        }
        return result;
    }

    /**
     * 记录一次文件访问
     *
     * @param userId 用户 ID
     * @param fileId 文件 ID
     */
    @Override
    public void recordAccess(Long userId, Long fileId) {
        fileRecentAccess.recordAccess(userId, fileId, LocalDateTime.now());
    }

    /**
     * 文件展示名补全后缀（文件夹不补）
     *
     * @param fileInfo 文件信息
     */
    private void appendSuffixIfFile(FileInfo fileInfo) {
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            return;
        }
        String suffix = fileInfo.getFileSuffix();
        if (suffix == null || suffix.isEmpty()) {
            return;
        }
        String filename = fileInfo.getFilename();
        if (filename == null) {
            return;
        }
        String lowerName = filename.toLowerCase();
        if (!lowerName.endsWith("." + suffix.toLowerCase())) {
            fileInfo.setFilename(filename + "." + suffix);
        }
    }

    /**
     * 构造带后缀的完整文件名
     *
     * @param fileInfo 文件信息
     * @return 完整文件名
     */
    private String buildFullName(FileInfo fileInfo) {
        String suffix = fileInfo.getFileSuffix();
        return fileInfo.getFilename() + (suffix == null || suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 复制文件信息（不含主键与时间戳等元数据）
     *
     * @param source 源文件
     * @return 新文件实体
     */
    private FileInfo cloneFileInfo(FileInfo source) {
        FileInfo copy = new FileInfo();
        copy.setFilename(source.getFilename());
        copy.setOriginalFilename(source.getOriginalFilename());
        copy.setPath(source.getPath());
        copy.setFileSize(source.getFileSize());
        copy.setFileType(source.getFileType());
        copy.setSha256Hash(source.getSha256Hash());
        copy.setFileSuffix(source.getFileSuffix());
        copy.setUserId(source.getUserId());
        copy.setParentId(source.getParentId());
        copy.setIsFolder(source.getIsFolder());
        copy.setIsDeleted(false);
        copy.setLocalFilePath(source.getLocalFilePath());
        copy.setCategoryId(source.getCategoryId());
        copy.setIsStarred(false);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        return copy;
    }

    /**
     * 解析复制后的新名称（冲突时追加 "(n)"）
     *
     * @param baseName  基础名
     * @param suffix    后缀（文件夹传 null）
     * @param parentId  父目录 ID
     * @param userId    用户 ID
     * @param isFolder  是否文件夹
     * @return 不冲突的显示名
     */
    private String resolveCopyName(String baseName, String suffix, Long parentId,
                                   Long userId, boolean isFolder) {
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(userId, parentId);
        String candidate = isFolder ? baseName : buildDisplayName(baseName, suffix);
        if (!hasCopyNameConflict(candidate, isFolder, siblings)) {
            return candidate;
        }
        for (int counter = 1; counter <= 999; counter++) {
            String next = isFolder
                    ? baseName + "(" + counter + ")"
                    : buildDisplayName(baseName + "(" + counter + ")", suffix);
            if (!hasCopyNameConflict(next, isFolder, siblings)) {
                return next;
            }
        }
        return candidate;
    }

    /**
     * 判断同级列表里是否已存在同名项
     *
     * @param name     待检查文件名
     * @param isFolder 是否文件夹
     * @param siblings 同级文件列表
     * @return true 表示冲突
     */
    private boolean hasCopyNameConflict(String name, boolean isFolder, List<FileInfo> siblings) {
        for (FileInfo sibling : siblings) {
            if (!Objects.equals(isFolder, Boolean.TRUE.equals(sibling.getIsFolder()))) {
                continue;
            }
            String siblingName = Boolean.TRUE.equals(sibling.getIsFolder())
                    ? sibling.getFilename()
                    : buildDisplayName(sibling.getFilename(), sibling.getFileSuffix());
            if (name.equalsIgnoreCase(siblingName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接带后缀的展示名
     *
     * @param name   基础名
     * @param suffix 后缀
     * @return 展示名
     */
    private String buildDisplayName(String name, String suffix) {
        return name + (suffix == null || suffix.isEmpty() ? "" : "." + suffix);
    }

    /**
     * 判断文件名是否包含 Windows 非法字符
     *
     * @param filename 文件名
     * @return true 表示含非法字符
     */
    private boolean hasIllegalCharacter(String filename) {
        for (char c : filename.toCharArray()) {
            if ("*?\"<>|".indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 目录路径规范化：统一以 "/" 结尾
     *
     * @param path 原始路径
     * @return 规范化路径
     */
    private String normalizeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.endsWith("/") ? path : path + "/";
    }
}
