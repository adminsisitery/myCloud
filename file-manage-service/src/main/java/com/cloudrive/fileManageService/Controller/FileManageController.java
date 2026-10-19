package com.cloudrive.fileManageService.Controller;

import com.cloudrive.fileManageService.handler.FileSearchHandler;
import com.cloudrive.fileManageService.model.dto.FileRenameDTO;
import com.cloudrive.fileManageService.model.dto.MoveFileDTO;
import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.fileManageService.service.FileManageService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件/文件夹管理控制器
 * <p>
 * 接口路径、HTTP 方法与限流阈值与原单体 {@code FileController} 保持一致，
 * 前端无需改动。上传/下载/秒传接口不在本服务；文件搜索在本服务内按
 * {@code file.search} 策略（Mysql 默认 / ES）实现。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/files")
public class FileManageController {

    private final FileManageService fileManageService;
    private final FileSearchHandler fileSearchHandler;

    public FileManageController(FileManageService fileManageService,
                                FileSearchHandler fileSearchHandler) {
        this.fileManageService = fileManageService;
        this.fileSearchHandler = fileSearchHandler;
    }

    /**
     * 获取指定目录下的文件/文件夹列表
     *
     * @param parentId 父目录 ID，默认根目录 0
     * @return 文件列表
     */
    @GetMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> listFiles(
            @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return Result.success(fileManageService.listFiles(parentId));
    }

    /**
     * 获取最近访问的文件列表
     *
     * @return 最近访问列表
     */
    @GetMapping("/recent")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> getRecentFiles() {
        return Result.success(fileManageService.getRecentFiles());
    }

    /**
     * 重命名文件/文件夹
     *
     * @param fileId 文件 ID
     * @param dto    重命名参数
     * @return 操作结果
     */
    @PatchMapping("/{fileId}/name")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> renameFile(@PathVariable("fileId") Long fileId,
                                     @Valid @RequestBody FileRenameDTO dto) {
        return fileManageService.renameFile(fileId, dto.getNewFilename());
    }

    /**
     * 移动文件/文件夹
     *
     * @param fileId 文件 ID
     * @param dto    移动参数
     * @return 操作结果
     */
    @PatchMapping("/{fileId}/move")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> moveFile(@PathVariable("fileId") Long fileId,
                                   @RequestBody MoveFileDTO dto) {
        return fileManageService.moveFile(fileId, dto.getTargetParentId());
    }

    /**
     * 复制文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @PostMapping("/copy/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 2.0, timeout = 1000)
    public Result<String> copyFile(@PathVariable("fileId") Long fileId) {
        return fileManageService.copyFile(fileId);
    }

    /**
     * 收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @PostMapping("/{fileId}/star")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> starFile(@PathVariable("fileId") Long fileId) {
        return fileManageService.starFile(fileId);
    }

    /**
     * 取消收藏文件/文件夹
     *
     * @param fileId 文件 ID
     * @return 操作结果
     */
    @DeleteMapping("/{fileId}/star")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 5.0, timeout = 500)
    public Result<String> unstarFile(@PathVariable("fileId") Long fileId) {
        return fileManageService.unstarFile(fileId);
    }

    /**
     * 获取收藏列表
     *
     * @return 收藏列表
     */
    @GetMapping("/starred")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP},
            permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> listStarredFiles() {
        return Result.success(fileManageService.listStarredFiles());
    }

    /**
     * 按关键词搜索当前用户的文件
     * <p>
     * 策略由 file.search 配置决定：Mysql（默认，数据库 LIKE）或 ES（Elasticsearch 索引）；
     * 返回结构与原单体 GET /api/files/search 一致。
     * </p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件列表
     */
    @GetMapping("/search")
    @RateLimit(dimensions = {RateLimit.Dimension.USER},
            permitsPerSecond = 5.0, timeout = 1000)
    public Result<List<FileListVO>> searchFiles(@RequestParam("keyword") String keyword) {
        return Result.success(fileSearchHandler.searchFiles(keyword));
    }
}
