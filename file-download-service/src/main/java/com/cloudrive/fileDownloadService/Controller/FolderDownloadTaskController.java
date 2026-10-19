package com.cloudrive.fileDownloadService.Controller;

import com.aliyun.oss.model.ObjectMetadata;
import com.cloudrive.fileDownloadService.model.vo.FolderDownloadTaskVO;
import com.cloudrive.fileDownloadService.service.FolderDownloadTaskService;
import com.cloudrive.fileDownloadService.service.StorageService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.domain.folder.FolderDownloadTask;
import com.cloudrive.pan_common.result.Result;
import com.cloudrive.pan_common.util.HttpRange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件夹异步打包任务控制器
 * <p>
 * 对应原单体 {@code /api/folder-download-tasks} 的 4 个接口：
 * 创建任务、查询状态、取消任务、下载打包好的 ZIP（支持 Range）。
 * 打包执行已迁到 file-async-service，本服务负责创建/查询/取消与结果下载。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/folder-download-tasks")
public class FolderDownloadTaskController {

    private static final Logger logger = LoggerFactory.getLogger(FolderDownloadTaskController.class);

    /** ZIP 下载缓冲区大小 */
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FolderDownloadTaskService folderDownloadTaskService;
    private final StorageService storageService;

    public FolderDownloadTaskController(FolderDownloadTaskService folderDownloadTaskService,
                                        StorageService storageService) {
        this.folderDownloadTaskService = folderDownloadTaskService;
        this.storageService = storageService;
    }

    /**
     * 创建文件夹打包任务
     *
     * @param folderId 文件夹ID
     * @return 任务视图
     */
    @PostMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 1.0, timeout = 1000)
    public Result<FolderDownloadTaskVO> create(@RequestParam Long folderId) {
        return Result.success(folderDownloadTaskService.create(folderId));
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务ID
     * @return 任务视图
     */
    @GetMapping("/{taskId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<FolderDownloadTaskVO> get(@PathVariable String taskId) {
        return Result.success(folderDownloadTaskService.getOwnedTask(taskId));
    }

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return 空结果
     */
    @DeleteMapping("/{taskId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 3.0, timeout = 500)
    public Result<Void> cancel(@PathVariable String taskId) {
        folderDownloadTaskService.cancel(taskId);
        return Result.success();
    }

    /**
     * 下载打包好的 ZIP（支持 Range 断点续传）
     *
     * @param taskId   任务ID
     * @param request  HTTP 请求（读取 Range 头）
     * @param response HTTP 响应
     */
    @GetMapping("/{taskId}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 4.0, timeout = 1000)
    public void download(@PathVariable String taskId,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        FolderDownloadTask task = folderDownloadTaskService.getDownloadableTask(taskId);
        ObjectMetadata metadata = storageService.getObjectMetadata(task.getObjectKey());
        long fileSize = metadata.getContentLength();
        String filename = task.getFolderName() == null || task.getFolderName().isBlank()
                ? "folder" : task.getFolderName();
        if (filename.toLowerCase().endsWith(".zip")) {
            filename = filename.substring(0, filename.length() - 4);
        }
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename + ".zip", StandardCharsets.UTF_8).build().toString());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        HttpRange range = null;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                range = HttpRange.parse(rangeHeader, fileSize);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                return;
            }
        }
        try (OutputStream output = response.getOutputStream()) {
            if (range != null) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        "bytes " + range.start() + "-" + range.end() + "/" + fileSize);
                response.setContentLengthLong(range.length());
                try (InputStream input = storageService.getFileRange(task.getObjectKey(), range.start(), range.end())) {
                    copy(input, output);
                }
            } else {
                response.setContentLengthLong(fileSize);
                try (InputStream input = storageService.getFile(task.getObjectKey())) {
                    copy(input, output);
                }
            }
        } catch (IOException e) {
            logger.error("文件夹压缩包下载失败: taskId={}, range={}", taskId, rangeHeader, e);
            throw new com.cloudrive.pan_common.exceotion.BusinessException(
                    com.cloudrive.pan_common.enums.ErrorCode.FILE_DOWNLOAD_FAILED, "文件夹压缩包下载失败");
        }
    }

    /**
     * 流复制
     *
     * @param input  输入流
     * @param output 输出流
     * @throws IOException 读写异常
     */
    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        output.flush();
    }
}
