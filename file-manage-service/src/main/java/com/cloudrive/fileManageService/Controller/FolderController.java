package com.cloudrive.fileManageService.Controller;

import com.cloudrive.fileManageService.model.dto.CreateFolderDTO;
import com.cloudrive.fileManageService.service.FolderService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件夹控制器
 * <p>
 * 接口契约与原单体 {@code FolderController#makeFolder} 保持一致：
 * POST /api/file/folder。文件夹的其他管理操作由 FileManageController、
 * RecycleBinController 统一提供。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/file")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * 新建文件夹
     *
     * @param dto 新建参数
     * @return 操作结果
     */
    @PostMapping("/folder")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 1.0, timeout = 1000)
    public Result<String> makeFolder(@RequestBody CreateFolderDTO dto) {
        return folderService.createFolder(dto.getParentId(), dto.getFolderName());
    }
}
