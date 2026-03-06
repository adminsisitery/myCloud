package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.UploadProgressMapper;
import com.ff.mycloud.model.dto.FileRenameDTO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.UpLoadProgressService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/14 13:16
 * @version 1.0
 */

@RestController
@RequestMapping("/api/files")
public class FileController {
    FileService fileService;
    UpLoadProgressService upLoadProgressService;
    UploadProgressMapper uploadProgressMapper;

    public FileController( FileService fileService, UpLoadProgressService upLoadProgressService , UploadProgressMapper uploadProgressMapper) {
        this.fileService = fileService;
        this.upLoadProgressService = upLoadProgressService;
        this.uploadProgressMapper = uploadProgressMapper;
    }
    //上传文件
    @PostMapping
    public Result<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId){
        String path = fileService.uploadFile(file, parentId);
        return Result.success(path);
    }

    //展示文件列表
    @GetMapping
    public Result<List<FileListVO>> listFiles(@RequestParam(value="parentId",required = false) Long parentId){
        List<FileListVO> fileListVOS = fileService.listFiles(parentId);
        return Result.success(fileListVOS);
    }

    //上传文件 任务追踪上传
    @PostMapping("/progress")
    public Result<String> uploadWithProgress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId",required = false) Long parentId
    ){
        try {
        //获取文件类型
        String fileType = file.getContentType();

        //获取唯一任务id
        String taskId = UUID.randomUUID().toString();

        //创建上传任务 （对象）
        upLoadProgressService.createUploadTask(taskId,file.getOriginalFilename(),file.getSize());
        //在主线程获取用户信息
        Long currentUserId = UserContext.getCurrentUserId();

        //检查不是不是空文件
        if (file.isEmpty()) {
            return Result.error("文件为空");
        }

        //创建临时文件夹
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        //创建临时文件
        File tmpFile = new File(tmpDir, taskId+"_"+file.getOriginalFilename());
        file.transferTo(tmpFile);

        //异步上传
            Thread uploadThread = new Thread(() -> {
                try {
                    fileService.uploadFileWithProgressFromPath(fileType,tmpFile.getAbsolutePath(), file.getOriginalFilename(), file.getSize(), parentId, taskId, currentUserId);
                }
                finally {
                    // 上传完成后删除临时文件
                    if (tmpFile.exists()) {
                        tmpFile.delete();
                    }
                }
            });
        uploadThread.setDaemon(true);
        uploadThread.start();
        return Result.success(taskId);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //下载文件
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> downloadFile(@PathVariable long fileId){
        byte[] content = fileService.downloadFile(fileId);
        HttpHeaders http = new HttpHeaders();
        http.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        http.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(fileService.getFilename(fileId), StandardCharsets.UTF_8)
                .build());
        http.setContentLength(content.length);

        return ResponseEntity.ok().headers(http).body(content);
    }


    //    改名文件
    @PatchMapping("/{fileId}/name")
    public Result<Void> renameFile(@PathVariable Long fileId, @Valid @RequestBody FileRenameDTO dto) {
        fileService.renameFile(fileId, dto.getNewFilename());
        return Result.success();
    }

    //删除文件
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        fileService.deleteFile(fileId);
        return Result.success();
    }


    @GetMapping("search")
    public Result<List<FileListVO>> searchFiles(@RequestParam String keyword){
        List<FileListVO> files = fileService.searchFiles(keyword);
        return Result.success(files);
    }
}
