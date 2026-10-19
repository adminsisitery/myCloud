package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.handler.FileSearchHandler;
import com.ff.mycloud.model.dto.FastUploadRequest;
import com.ff.mycloud.model.dto.FileRenameDTO;
import com.ff.mycloud.model.dto.MoveFileDTO;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.vo.FastUploadVO;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.DeleteFileService;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.FileUploadTaskService;
import com.ff.mycloud.service.StorageServiceFactory;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    private final StorageServiceFactory storageServiceFactory;

    /**
     * 构造器注入文件服务相关依赖
     *
     * @param fileService           文件核心服务
     * @param fileUploadTaskService 文件上传任务服务
     * @param fileSearchHandler     文件搜索处理器
     * @param deleteFileService     文件删除服务（回收站）
     */
    public FileController(FileService fileService, FileUploadTaskService fileUploadTaskService,
                          FileSearchHandler fileSearchHandler, DeleteFileService deleteFileService,
                          StorageServiceFactory storageServiceFactory) {
        this.fileService = fileService;
        this.fileUploadTaskService = fileUploadTaskService;
        this.fileSearchHandler = fileSearchHandler;
        this.deleteFileService = deleteFileService;
        this.storageServiceFactory = storageServiceFactory;
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
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable long fileId,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        FileDownloadVO file = fileService.prepareDownload(fileId);
        HttpHeaders http = new HttpHeaders();
        try {
            http.setContentType(MediaType.parseMediaType(file.contentType()));
        } catch (Exception ignored) {
            http.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        http.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build());
        http.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        long start = 0;
        long end = file.size() - 1;
        HttpStatus status = HttpStatus.OK;
        if (rangeHeader != null) {
            try {
                HttpRange range = HttpRange.parse(rangeHeader, file.size());
                start = range.start();
                end = range.end();
                status = HttpStatus.PARTIAL_CONTENT;
                http.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + file.size());
            } catch (IllegalArgumentException e) {
                http.set(HttpHeaders.CONTENT_RANGE, "bytes */" + file.size());
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(http).build();
            }
        }
        long contentLength = file.size() == 0 ? 0 : end - start + 1;
        http.setContentLength(contentLength);
        long rangeStart = start;
        long rangeEnd = end;
        StreamingResponseBody body = outputStream -> {
            if (file.size() == 0) return;
            try (var input = storageServiceFactory.getStorageService()
                    .getFileRange(file.path(), rangeStart, rangeEnd)) {
                input.transferTo(outputStream);
            }
        };
        return ResponseEntity.status(status).headers(http).body(body);
    }

    /**
     * 获取最近访问文件
     * <p>返回当前用户最近下载过的文件，按访问时间倒序，最多 {@code CommonConstants.RecentFile.N} 条。</p>
     *
     * @return 最近访问的文件列表
     */
    @GetMapping("/recent")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> getRecentFiles() {
        return Result.success(fileService.getRecentFiles());
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
     * 文件秒传检测（判定阶段，无副作用）
     * <p>
     * 按 SHA-256 + 文件大小检测该文件是否已在服务器中存在。命中则客户端可跳过上传字节，
     * 但本接口<b>不会</b>落库、也不返回文件信息；真正的落库由
     * {@code POST /api/files/fast-upload} 完成。
     * </p>
     *
     * @param fileCheck 包含 sha256Hash 与 fileSize 的检测参数
     * @return 文件检测结果 VO，exists=true 表示可秒传
     */
    @PostMapping("/check")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<FileCheckVO> checkFiles(@RequestParam FileCheck fileCheck) {
        return fileService.checkFilesHash(fileCheck);
    }

    /**
     * 文件复制
     * /api/files/copy/{fileId}
     */
    @PostMapping("/copy/{fileId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<Void> copyFile(@PathVariable Long fileId){
        fileService.copyFile(fileId);
        return Result.success();
    }

    /**
     * 收藏文件
     * <p>对指定文件添加星标，已收藏的文件重复调用不影响。</p>
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @PostMapping("/{fileId}/star")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> starFile(@PathVariable Long fileId) {
        fileService.starFile(fileId);
        return Result.success();
    }

    /**
     * 取消收藏文件
     * <p>移除指定文件的星标。</p>
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @DeleteMapping("/{fileId}/star")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> unstarFile(@PathVariable Long fileId) {
        fileService.unstarFile(fileId);
        return Result.success();
    }

    /**
     * 获取收藏文件列表
     * <p>返回当前用户所有已收藏且未被删除的文件。</p>
     *
     * @return 收藏文件列表
     */
    @GetMapping("/starred")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 10.0, timeout = 500)
    public Result<List<FileListVO>> listStarredFiles() {
        return Result.success(fileService.listStarredFiles());
    }


    /**
     * 文件秒传执行（落库阶段）
     * <p>
     * 秒传链路的后半段。客户端先用 {@code POST /api/files/check} 判定，命中后再调本接口在目标目录落库，
     * 全程不传输文件字节。服务端不信任请求内容：会重新按 sha256 + fileSize 确认物理对象存在，
     * 并保证幂等——目标目录已有同内容同名记录时返回原记录，不重复插入。
     * </p>
     *
     * @param request 秒传请求体（fileName / fileSize / sha256Hash / parentId），JSON
     * @return 落库结果，{@code data.fileId} 为文件记录ID
     */
    @PostMapping("/fast-upload")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<FastUploadVO> fastUpload(@RequestBody FastUploadRequest request) {
        return fileService.fastUpload(request);
    }
}
