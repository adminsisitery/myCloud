package com.cloudrive.fileDownloadService.Controller;

import com.cloudrive.fileDownloadService.service.FolderArchiveService;
import com.cloudrive.pan_common.annotation.RateLimit;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件夹下载控制器
 * <p>
 * 对应原单体 {@code GET /api/file/{fileId}/content}：把当前用户的文件夹
 * 整棵子树流式打包为 ZIP 下载。接口契约与单体一致（用户+IP 限流 2 次/秒）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/file")
public class FolderController {

    private final FolderArchiveService folderArchiveService;

    public FolderController(FolderArchiveService folderArchiveService) {
        this.folderArchiveService = folderArchiveService;
    }

    /**
     * 同步打包下载文件夹
     *
     * @param fileId   文件夹ID
     * @param response HTTP 响应（ZIP 流）
     */
    @GetMapping("/{fileId}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public void downloadFolder(@PathVariable long fileId, HttpServletResponse response) {
        folderArchiveService.downLoadFolder(fileId, response);
    }
}
