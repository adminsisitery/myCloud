package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.service.FileService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/14 13:16
 * @version 1.0
 */

@Controller
public class FileController {
    FileService fileService;

    //上传文件
    public Result<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("parentId") Long parentId){
        String path = fileService.uploadFile(file, parentId);
        return Result.success(path);
    }
}
