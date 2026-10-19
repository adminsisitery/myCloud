package com.cloudrive.filePreviewService.service;

import com.cloudrive.filePreviewService.model.vo.PreviewVO;

/**
 * 文件预览服务
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface PreviewService {

    /**
     * 获取文件预览信息
     * <p>校验当前用户是否为文件所有者、文件是否支持预览，并生成 OSS 预签名 URL。</p>
     *
     * @param fileId 文件ID
     * @return 预览信息（URL + 分类 + MIME + 文件名 + 大小）
     */
    PreviewVO preview(Long fileId);
}
