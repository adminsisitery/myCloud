package com.cloudrive.filePreviewService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.filePreviewService.config.properties.OssProProperties;
import com.cloudrive.filePreviewService.model.entity.FileInfo;
import com.cloudrive.filePreviewService.model.vo.PreviewVO;
import com.cloudrive.filePreviewService.repositorys.FileInfoMapper;
import com.cloudrive.filePreviewService.service.OssStorageService;
import com.cloudrive.filePreviewService.service.PreviewService;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.exceotion.BusinessException;
import com.cloudrive.pan_common.util.FileTypeUtil;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.stereotype.Service;

/**
 * 文件预览服务实现
 * <p>
 * 逻辑从原单体 {@code PreviewController#preview} 原样迁移：
 * 先按 id + userId + 未删除 校验归属（非本人文件统一返回无权限），
 * 再判断后缀是否支持在线预览，最后生成 5 分钟有效的 OSS 预签名 URL。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class PreviewServiceImpl implements PreviewService {

    /** 预签名 URL 有效期：5 分钟 */
    private static final int URL_EXPIRY_SECONDS = 300;

    private final FileInfoMapper fileInfoMapper;
    private final OssStorageService ossStorageService;
    private final OssProProperties ossProProperties;

    public PreviewServiceImpl(FileInfoMapper fileInfoMapper,
                              OssStorageService ossStorageService,
                              OssProProperties ossProProperties) {
        this.fileInfoMapper = fileInfoMapper;
        this.ossStorageService = ossStorageService;
        this.ossProProperties = ossProProperties;
    }

    @Override
    public PreviewVO preview(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo fileInfo = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .last("limit 1"));
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        String suffix = fileInfo.getFileSuffix();
        if (!FileTypeUtil.isPreviewSupported(suffix)) {
            throw new BusinessException(ErrorCode.FILE_NOT_SUPPORT_PREVIEW);
        }

        // 生成 OSS 预签名 URL（5 分钟有效期）；mimeType 仅用于覆盖 Content-Type 的兼容入参
        String mimeType = FileTypeUtil.getContentTypeFromFileSuffix(suffix);
        String url = ossStorageService.generatePresignedUrl(fileInfo.getPath(), URL_EXPIRY_SECONDS, mimeType);

        String fullName = fileInfo.getFilename();
        if (suffix != null && !suffix.isEmpty()) {
            fullName = fullName + "." + suffix;
        }

        return new PreviewVO(
                url,
                FileTypeUtil.getPreviewCategory(suffix),
                mimeType,
                fullName,
                fileInfo.getFileSize()
        );
    }
}
