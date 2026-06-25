package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.dto.FileRenameDTO;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FileUploadTaskService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileUploadTaskService fileUploadTaskService;

    public FileController(FileService fileService, FileUploadTaskService fileUploadTaskService) {
        this.fileService = fileService;
        this.fileUploadTaskService = fileUploadTaskService;
    }

    @GetMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> listFiles(@RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return Result.success(fileService.listFiles(parentId));
    }

    @PostMapping("/progress")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<String> uploadWithProgress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return fileUploadTaskService.uploadWithProgress(file, parentId);
    }

    @GetMapping("/{fileId}/content")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public ResponseEntity<byte[]> downloadFile(@PathVariable long fileId) {
        byte[] content = fileService.downloadFile(fileId);
        HttpHeaders http = new HttpHeaders();
        http.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        http.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(fileService.getFilename(fileId), StandardCharsets.UTF_8)
                .build());
        http.setContentLength(content.length);
        return ResponseEntity.ok().headers(http).body(content);
    }

    @PatchMapping("/{fileId}/name")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> renameFile(@PathVariable Long fileId, @Valid @RequestBody FileRenameDTO dto) {
        fileService.renameFile(fileId, dto.getNewFilename());
        return Result.success();
    }

    @DeleteMapping("/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        fileService.deleteFile(fileId);
        return Result.success();
    }

    @GetMapping("/search")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 1000)
    public Result<List<FileListVO>> searchFiles(@RequestParam String keyword) {
        return Result.success(fileService.searchFiles(keyword));
    }

    @PostMapping("/check")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<FileCheckVO> checkFiles(@RequestParam FileCheck fileCheck) {
        return fileService.checkFilesHash(fileCheck);
    }


//    @PostMapping("/fast-upload")
//    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
//    public Result<FileCheckVO> checkFiles(@RequestParam FileCheck fileCheck) {
//        return fileService.checkFilesHash(fileCheck);
//    }
}
