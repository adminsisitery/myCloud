package com.cloudrive.fileManageService.Controller;

import com.cloudrive.fileManageService.model.vo.TrashListVO;
import com.cloudrive.fileManageService.service.RecycleBinService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回收站控制器
 * <p>
 * 接口路径、HTTP 方法与限流阈值与原单体 {@code deleteFileController} 保持一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/files")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    public RecycleBinController(RecycleBinService recycleBinService) {
        this.recycleBinService = recycleBinService;
    }

    /**
     * 批量删除文件/文件夹（进入回收站）
     *
     * @param ids 逗号分隔的文件 ID
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> batchDeleteFile(@RequestParam("ids") String ids) {
        return recycleBinService.batchDeleteFile(ids);
    }

    /**
     * 彻底删除文件/文件夹（不可恢复）
     *
     * @param fileIds 逗号分隔的文件 ID
     * @return 操作结果
     */
    @DeleteMapping("/tureDelete")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> tureDeleteFile(@RequestParam("fileIds") String fileIds) {
        return recycleBinService.tureDeleteFile(fileIds);
    }

    /**
     * 删除单个文件/文件夹（进入回收站）
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @DeleteMapping("/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> deleteFile(@PathVariable("fileId") Long fileId) {
        return recycleBinService.deleteSingleFile(fileId);
    }

    /**
     * 获取回收站列表
     *
     * @return 回收站列表
     */
    @GetMapping("/trash")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<List<TrashListVO>> getTrashList() {
        return Result.success(recycleBinService.getTrashList());
    }

    /**
     * 从回收站恢复文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @PutMapping("/trash/restore/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> restoreFile(@PathVariable("fileId") Long fileId) {
        return recycleBinService.restoreFile(fileId);
    }

    /**
     * 清空回收站
     *
     * @return 操作结果
     */
    @DeleteMapping("/trash/clean")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> cleanTrash() {
        return recycleBinService.cleanTrash();
    }
}
