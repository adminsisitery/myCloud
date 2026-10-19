package com.cloudrive.fileShareService.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 分享内容下载服务接口
 * <p>
 * 分享场景下的内容下载：单文件由 {@link FileShareService#downloadSharedFile} 处理，
 * 文件夹在此以 ZIP 流式打包输出。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FolderShareService {

    /**
     * 打包下载分享的文件夹（分享场景，非当前登录用户本人）
     *
     * @param fileId      文件夹 ID
     * @param ownerUserId 分享者用户 ID
     * @param response    HTTP 响应（ZIP 流写入）
     */
    void downLoadSharedFolder(long fileId, Long ownerUserId, HttpServletResponse response);
}
