package com.cloudrive.aiService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.service.FileInfoMapperService;
import com.cloudrive.aiService.service.FolderService;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 文件夹服务实现
 * <p>创建文件夹逻辑照搬单体 FolderServiceImpl.createFolder，保持校验与错误信息一致。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FolderServiceImpl implements FolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderServiceImpl.class);

    private static final int MAX_FOLDER_NAME_LENGTH = 30;
    private static final Pattern ILLEGAL_CHARS = Pattern.compile(".*[\\\\/:*?\"<>|].*");

    private final FileInfoMapperService fileInfoMapperService;

    public FolderServiceImpl(FileInfoMapperService fileInfoMapperService) {
        this.fileInfoMapperService = fileInfoMapperService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> createFolder(Long parentId, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            return Result.error(500, "文件夹名不能为空");
        }
        folderName = folderName.trim();
        Long currentUserId = UserContext.getCurrentUserId();
        if (folderName.length() > MAX_FOLDER_NAME_LENGTH) {
            return Result.error(500, "名称过长");
        }
        if (ILLEGAL_CHARS.matcher(folderName).matches()) {
            return Result.error(500, "文件名包含非法字符");
        }
        Long normalizedParentId = parentId == null ? 0L : parentId;

        boolean exists = fileInfoMapperService.exists(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getParentId, normalizedParentId)
                .eq(FileInfo::getIsFolder, true)
                .eq(FileInfo::getFilename, folderName));
        if (exists) {
            return Result.error(500, "同级目录下已存在同名文件夹");
        }

        String localFilePath;
        if (normalizedParentId == 0L) {
            localFilePath = folderName + "/";
        } else {
            FileInfo parent = fileInfoMapperService.getById(normalizedParentId);
            if (parent == null
                    || !currentUserId.equals(parent.getUserId())
                    || !Boolean.TRUE.equals(parent.getIsFolder())
                    || Boolean.TRUE.equals(parent.getIsDeleted())) {
                return Result.error(404, "父文件夹不存在或无权访问");
            }
            localFilePath = normalizeDirectory(parent.getLocalFilePath()) + folderName + "/";
        }

        LocalDateTime now = LocalDateTime.now();
        FileInfo folder = new FileInfo();
        folder.setFilename(folderName);
        folder.setOriginalFilename(folderName);
        folder.setPath("/");
        folder.setFileSize(0L);
        folder.setFileType("folder");
        folder.setUserId(currentUserId);
        folder.setParentId(normalizedParentId);
        folder.setIsFolder(true);
        folder.setIsDeleted(false);
        folder.setLocalFilePath(localFilePath);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        folder.setIsStarred(false);
        folder.setCategoryId(FileTypeUtil.getCategoryId(null));
        fileInfoMapperService.save(folder);

        log.info("创建文件夹成功: userId={}, folderId={}, name={}", currentUserId, folder.getId(), folderName);
        return Result.success("创建成功");
    }

    private String normalizeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
