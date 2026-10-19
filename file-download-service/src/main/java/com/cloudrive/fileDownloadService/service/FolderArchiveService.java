package com.cloudrive.fileDownloadService.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件夹同步打包下载服务
 * <p>
 * 对应原单体的 {@code GET /api/file/{fileId}/content}：把当前用户的文件夹
 * 整棵子树流式打成 ZIP 直接写回响应（不落临时文件）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FolderArchiveService {

    /**
     * 同步打包下载文件夹
     *
     * @param fileId   文件夹ID
     * @param response HTTP 响应
     */
    void downLoadFolder(long fileId, HttpServletResponse response);
}
