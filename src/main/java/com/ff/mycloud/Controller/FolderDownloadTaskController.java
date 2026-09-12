package com.ff.mycloud.Controller;

import com.aliyun.oss.model.ObjectMetadata;
import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.HttpRange;
import com.ff.mycloud.model.entity.FolderDownloadTask;
import com.ff.mycloud.model.vo.FolderDownloadTaskVO;
import com.ff.mycloud.service.FolderDownloadTaskService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/folder-download-tasks")
public class FolderDownloadTaskController {
    private final FolderDownloadTaskService service;
    private final StorageServiceFactory storageFactory;

    public FolderDownloadTaskController(FolderDownloadTaskService service,
                                        StorageServiceFactory storageFactory) {
        this.service = service;
        this.storageFactory = storageFactory;
    }

    @PostMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 1.0, timeout = 1000)
    public Result<FolderDownloadTaskVO> create(@RequestParam Long folderId) {
        return Result.success(service.create(folderId));
    }

    @GetMapping("/{taskId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<FolderDownloadTaskVO> get(@PathVariable String taskId) {
        return Result.success(service.getOwnedTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 3.0, timeout = 500)
    public Result<Void> cancel(@PathVariable String taskId) {
        service.cancel(taskId);
        return Result.success();
    }

    @GetMapping("/{taskId}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 4.0, timeout = 1000)
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String taskId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        FolderDownloadTask task = service.getDownloadableTask(taskId);
        StorageService storage = storageFactory.getStorageService();
        ObjectMetadata metadata = storage.getObjectMetadata(task.getObjectKey());
        long size = metadata.getContentLength();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(task.getFolderName() + ".zip", StandardCharsets.UTF_8).build());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        long start = 0;
        long end = size - 1;
        HttpStatus status = HttpStatus.OK;
        if (rangeHeader != null) {
            try {
                HttpRange range = HttpRange.parse(rangeHeader, size);
                start = range.start();
                end = range.end();
                status = HttpStatus.PARTIAL_CONTENT;
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size);
            } catch (IllegalArgumentException e) {
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
            }
        }
        headers.setContentLength(size == 0 ? 0 : end - start + 1);
        long rangeStart = start;
        long rangeEnd = end;
        StreamingResponseBody body = output -> {
            if (size == 0) return;
            try (var input = storage.getFileRange(task.getObjectKey(), rangeStart, rangeEnd)) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
