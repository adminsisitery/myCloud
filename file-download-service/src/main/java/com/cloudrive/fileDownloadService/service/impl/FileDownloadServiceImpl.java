package com.cloudrive.fileDownloadService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.fileDownloadService.model.entity.FileInfo;
import com.cloudrive.fileDownloadService.model.vo.FileDownloadVO;
import com.cloudrive.fileDownloadService.repositorys.FileInfoMapper;
import com.cloudrive.fileDownloadService.service.FileDownloadService;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文件下载服务实现
 * <p>
 * 从单体 {@code com.ff.mycloud.service.impl.FileServiceImpl#prepareDownload} 迁入：
 * 校验文件存在、归属当前用户、未被删除、不是文件夹，然后返回下载元信息。
 * 与单体的差异：新服务暂不记录“最近访问”（该功能仍在单体，待文件管理整体迁移时再引入）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Service
public class FileDownloadServiceImpl implements FileDownloadService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FileInfoMapper fileInfoMapper;
    private final StorageService storageService;

    public FileDownloadServiceImpl(FileInfoMapper fileInfoMapper, StorageService storageService) {
        this.fileInfoMapper = fileInfoMapper;
        this.storageService = storageService;
    }

    @Override
    public FileDownloadVO prepareDownload(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        // 查询条件已过滤 is_deleted：查不到统一按“不存在”处理，避免泄漏其他用户文件的存在性
        FileInfo file = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .last("limit 1"));
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            throw new BusinessException(ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = file.getFilename();
            if (file.getFileSuffix() != null && !file.getFileSuffix().isBlank()) {
                filename += "." + file.getFileSuffix();
            }
        }
        String contentType = file.getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        long size = file.getFileSize() == null ? 0L : file.getFileSize();
        return new FileDownloadVO(file.getPath(), filename, contentType, size);
    }

    @Override
    public InputStream openRange(String path, long start, long end) {
        return storageService.getFileRange(path, start, end);
    }
}
