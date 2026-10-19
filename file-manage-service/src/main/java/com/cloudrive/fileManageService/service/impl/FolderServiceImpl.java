package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.mapper.FileMapper;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.FolderService;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文件夹服务实现类
 * <p>
 * 新建文件夹逻辑从原单体 {@code FolderServiceImpl#createFolder} 迁移，
 * 校验规则（空名、长度、非法字符、同级重名、父目录归属）保持一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class FolderServiceImpl implements FolderService {

    private static final Logger logger = LoggerFactory.getLogger(FolderServiceImpl.class);

    /** 文件夹名最大长度 */
    private static final int MAX_FOLDER_NAME_LENGTH = 30;

    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;

    public FolderServiceImpl(FileInfoMapperService fileInfoMapperService, FileMapper fileMapper) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
    }

    /**
     * 新建文件夹
     *
     * @param parentId   父目录 ID（0 或 null 表示根目录）
     * @param folderName 文件夹名称
     * @return 操作结果
     */
    @Override
    public Result<String> createFolder(Long parentId, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            return Result.error("文件夹名不能为空");
        }
        String name = folderName.trim();
        Long currentUserId = UserContext.getCurrentUserId();
        if (name.length() > MAX_FOLDER_NAME_LENGTH) {
            return Result.error("文件夹名长度不能超过30个字符");
        }
        if (name.contains("/") || name.contains("\\") || name.contains(":")
                || hasIllegalCharacter(name)) {
            return Result.error("文件夹名包含非法字符");
        }
        Long normalizedParentId = (parentId == null) ? 0L : parentId;
        if (fileInfoMapperService.existsFolder(normalizedParentId, name, currentUserId)) {
            return Result.error("同级目录下已存在同名文件夹");
        }

        String localFilePath;
        if (normalizedParentId == 0L) {
            localFilePath = name + "/";
        } else {
            FileInfo parent = fileInfoMapperService.findByIdAndUserId(normalizedParentId, currentUserId);
            if (parent == null || !Boolean.TRUE.equals(parent.getIsFolder())) {
                return Result.error("父文件夹不存在或无权访问");
            }
            localFilePath = normalizeDirectory(parent.getLocalFilePath()) + name + "/";
        }

        FileInfo folder = fileMapper.toFileInfoFromFolder(
                name, true, false, normalizedParentId, currentUserId, localFilePath);
        folder.setCategoryId(FileTypeUtil.getCategoryId(null));
        fileInfoMapperService.save(folder);
        logger.info("创建文件夹成功: userId={}, parentId={}, name={}", currentUserId, normalizedParentId, name);
        return Result.success("创建成功");
    }

    /**
     * 判断文件夹名是否包含 Windows 非法字符
     *
     * @param name 文件夹名
     * @return true 表示含非法字符
     */
    private boolean hasIllegalCharacter(String name) {
        for (char c : name.toCharArray()) {
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
