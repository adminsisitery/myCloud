package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.vo.TrashListVO;
import com.ff.mycloud.service.DeleteFileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回收站 & 批量删除控制器
 * <p>提供文件软删除（移入回收站）、批量删除、彻底删除、恢复等接口。</p>
 *
 * @author 飞飞
 * @date 2026/06/29
 */
@RestController
@RequestMapping("/api/files")
public class deleteFileController {

    private final DeleteFileService deleteFileService;

    public deleteFileController(DeleteFileService deleteFileService) {
        this.deleteFileService = deleteFileService;
    }

    /**
     * 批量删除文件（移入回收站）
     *
     * @param ids 文件ID列表
     * @return 操作结果
     */
    @DeleteMapping("/batch")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<?> batchDelete(@RequestParam String ids) {
        deleteFileService.batchDeleteFile(parseIds(ids));
        return Result.success();
    }

    @DeleteMapping("/tureDelete")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<?> tureDelete(@RequestParam String ids) {
        deleteFileService.tureDeleteFile(parseIds(ids));
        return Result.success();
    }

    private List<Long> parseIds(String ids) {
        String[] parts = ids.split(",");
        List<Long> result = new java.util.ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(Long.parseLong(trimmed));
            }
        }
        return result;
    }
    /**
     * 删除单个文件
     * <p>
     * 根据文件ID删除文件（支持逻辑删除或物理删除，由服务层决定）。
     * </p>
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @DeleteMapping("/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        deleteFileService.deleteSingleFile(fileId);
        return Result.success();
    }

    /**
     * 回收站列表
     */
    @GetMapping("/trash")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<List<TrashListVO>> getTrashList() {
        return Result.success(deleteFileService.getTrashList());
    }

    /**
     * 恢复文件
     */
    @PutMapping("/trash/restore/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> restoreFile(@PathVariable Long fileId) {
        deleteFileService.restoreFile(fileId);
        return Result.success();
    }

    /**
     * 清空回收站
     */
    @DeleteMapping("/trash/clean")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> clean() {
        deleteFileService.cleanTrash();
        return Result.success();
    }
}
