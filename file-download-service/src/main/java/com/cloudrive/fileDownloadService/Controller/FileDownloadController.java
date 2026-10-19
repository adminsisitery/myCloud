package com.cloudrive.fileDownloadService.Controller;

import com.cloudrive.fileDownloadService.model.vo.FileDownloadVO;
import com.cloudrive.fileDownloadService.service.FileDownloadService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.util.HttpRange;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/**
 * 文件下载控制器
 * <p>
 * 从单体 {@code com.ff.mycloud.Controller.FileController#downloadFile} 迁入，
 * 对外契约保持不变：{@code GET /api/files/{fileId}/content}。
 * 支持 HTTP Range 断点续传（单段 bytes），响应为流式输出，不把整个文件读入内存。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@RestController
@RequestMapping("/api/files")
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    public FileDownloadController(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }

    /**
     * 下载文件
     * <p>
     * 根据文件ID下载文件二进制内容，设置 Content-Disposition / Content-Type / Content-Length 响应头；
     * 请求头带 Range 时返回 206 Partial Content，区间非法时返回 416。
     * </p>
     *
     * @param fileId      文件ID
     * @param rangeHeader Range 请求头（可空）
     * @return 流式响应实体
     */
    @GetMapping("/{fileId}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable long fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        FileDownloadVO file = fileDownloadService.prepareDownload(fileId);
        HttpHeaders http = new HttpHeaders();
        try {
            http.setContentType(MediaType.parseMediaType(file.contentType()));
        } catch (Exception ignored) {
            http.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        http.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build());
        http.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        long size = file.size();
        long start = 0;
        long end = size - 1;
        HttpStatus status = HttpStatus.OK;
        if (rangeHeader != null) {
            try {
                HttpRange range = HttpRange.parse(rangeHeader, size);
                start = range.start();
                end = range.end();
                status = HttpStatus.PARTIAL_CONTENT;
                http.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size);
            } catch (IllegalArgumentException e) {
                http.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(http).build();
            }
        }
        long contentLength = size == 0 ? 0 : end - start + 1;
        http.setContentLength(contentLength);
        long rangeStart = start;
        long rangeEnd = end;
        StreamingResponseBody body = outputStream -> {
            if (size == 0) {
                return;
            }
            try (var input = fileDownloadService.openRange(file.path(), rangeStart, rangeEnd)) {
                input.transferTo(outputStream);
            }
        };
        return ResponseEntity.status(status).headers(http).body(body);
    }
}
