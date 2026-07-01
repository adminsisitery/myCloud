package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.handler.MySQLFileSearchHandler;
import com.ff.mycloud.model.dto.FileRenameDTO;
import com.ff.mycloud.model.dto.MoveFileDTO;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.DeleteFileService;
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

/**
 * 文件管理控制器
 * <p>
 * 提供文件的核心 CRUD 操作接口，包括文件列表查询、上传、下载、重命名、删除、
 * 搜索以及文件秒传检测。所有接口均启用了 IP + 用户维度的限流保护。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileUploadTaskService fileUploadTaskService;
    private final FileSearchHandler fileSearchHandler;
    private final DeleteFileService deleteFileService;

    /**
     * 构造器注入文件服务相关依赖
     *
     * @param fileService           文件核心服务
     * @param fileUploadTaskService 文件上传任务服务
     * @param fileSearchHandler     文件搜索处理器
     * @param deleteFileService     文件删除服务（回收站）
     */
    public FileController(FileService fileService, FileUploadTaskService fileUploadTaskService,
                          FileSearchHandler fileSearchHandler, DeleteFileService deleteFileService) {
        this.fileService = fileService;
        this.fileUploadTaskService = fileUploadTaskService;
        this.fileSearchHandler = fileSearchHandler;
        this.deleteFileService = deleteFileService;
    }

    /**
     * 查询文件列表
     * <p>
     * 根据父目录ID（parentId）获取当前目录下的文件列表，默认 parentId 为 0（根目录）。
     * </p>
     *
     * @param parentId 父目录ID，0 表示根目录
     * @return 文件列表 VO 集合
     */
    @GetMapping
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> listFiles(@RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return Result.success(fileService.listFiles(parentId));
    }

    /**
     * 带进度回调的文件上传
     * <p>
     * 接收前端上传的文件流，异步处理上传并可通过任务ID查询上传进度。
     * </p>
     *
     * @param file     上传的文件（MultipartFile）
     * @param parentId 父目录ID
     * @return 上传任务ID
     */
    @PostMapping("/progress")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<String> uploadWithProgress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return fileUploadTaskService.uploadWithProgress(file, parentId);
    }

    /**
     * 下载文件
     * <p>
     * 根据文件ID下载文件二进制内容，设置正确的 Content-Disposition 和 Content-Type 响应头。
     * </p>
     *
     * @param fileId 文件ID
     * @return ResponseEntity 包含文件字节数组的响应实体
     */
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

    /**
     * 重命名文件
     * <p>
     * 修改指定文件的名称，新的文件名通过请求体传入。
     * </p>
     *
     * @param fileId 文件ID
     * @param dto    包含新文件名的 DTO
     * @return 操作结果
     */
    @PatchMapping("/{fileId}/name")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> renameFile(@PathVariable Long fileId, @Valid @RequestBody FileRenameDTO dto) {
        fileService.renameFile(fileId, dto.getNewFilename());
        return Result.success();
    }


    /**
     * 移动文件/文件夹
     * <p>
     * 将文件或文件夹移动到新的父目录下，更新 parentId 和 localFilePath。
     * 文件夹会递归更新所有子节点的路径。
     * </p>
     *
     * @param fileId 文件ID
     * @param dto    包含目标父目录ID的 DTO
     * @return 操作结果
     */
    @PatchMapping("/{fileId}/move")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> moveFile(@PathVariable Long fileId, @RequestBody MoveFileDTO dto) {
        fileService.moveFile(fileId, dto.getTargetParentId());
        return Result.success();
    }

    /**
     * 全文搜索文件
     * <p>
     * 根据关键词搜索当前用户拥有的文件，由 FileSearchHandler 决定使用 ES 或 MySQL 搜索策略。
     * </p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件列表
     */
    @GetMapping("/search")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 1000)
    public Result<List<FileListVO>> searchFiles(@RequestParam String keyword) {
        return Result.success(fileSearchHandler.searchFiles(keyword));
    }

    /**
     * 文件秒传检测
     * <p>
     * 根据文件的哈希值检测该文件是否已存在于服务器中。
     * 如果已存在则无需再次上传，直接返回已有文件信息（秒传机制）。
     * </p>
     *
     * @param fileCheck 包含文件哈希等信息的检测参数
     * @return 文件检测结果 VO
     */
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
