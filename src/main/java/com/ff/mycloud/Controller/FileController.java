package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.FileService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    public FileController(FileService fileService) {
        this.fileService = fileService;
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
}
