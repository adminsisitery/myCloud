package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.dto.CreateFolderDTO;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FolderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/10 22:03
 * @version 1.0
 */

@RequestMapping("/api/file")
@RestController
public class FolderController {

    FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    //新建文件夹
    @PostMapping("/folder")
    public Result<String> makeFolder(@RequestBody CreateFolderDTO dto) {
        return  folderService.createFolder(dto.getParentId(),dto.getFolderName());
    }

    //下载文件夹
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> downloadFile(@PathVariable long fileId, HttpServletResponse response) {
        folderService.downLoadFolder(fileId,response);
        return null;
    }
}
