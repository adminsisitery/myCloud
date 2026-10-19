package com.cloudrive.fileService.Controller;

import com.cloudrive.fileService.model.dto.FastUploadRequest;
import com.cloudrive.fileService.model.entity.FileCheck;
import com.cloudrive.fileService.model.vo.FastUploadVO;
import com.cloudrive.fileService.model.vo.FileCheckVO;
import com.cloudrive.fileService.service.FileFastUploadService;
import com.cloudrive.fileService.service.FileUploadTaskService;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.Controller.FileController} 中与上传相关的接口，
 * 对外契约保持不变：普通上传 {@code POST /api/files/progress}、秒传判定
 * {@code POST /api/files/check}、秒传执行 {@code POST /api/files/fast-upload}。
 * 网关按这三个精确路径把请求路由到文件服务；其余 {@code /api/files/**}
 * （列表/重命名/移动/分享等文件管理接口）仍由单体兜底，等整体迁完后统一切走。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileUploadTaskService fileUploadTaskService;
    private final FileFastUploadService fileFastUploadService;

    public FileUploadController(FileUploadTaskService fileUploadTaskService,
                                FileFastUploadService fileFastUploadService) {
        this.fileUploadTaskService = fileUploadTaskService;
        this.fileFastUploadService = fileFastUploadService;
    }

    /**
     * 普通文件上传（带进度）
     * <p>同步返回任务ID，文件内容由异步线程上传；前端轮询
     * {@code GET /api/upload-progress/{taskId}} 获取进度。</p>
     *
     * @param file     上传的文件
     * @param parentId 目标父目录ID，默认根目录
     * @return 上传任务ID
     */
    @PostMapping("/progress")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<String> uploadWithProgress(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "parentId", required = false, defaultValue = "0") Long parentId) {
        return fileUploadTaskService.uploadWithProgress(file, parentId);
    }

    /**
     * 文件秒传判定（判定阶段，无副作用）
     * <p>按 SHA-256 + 文件大小检测当前用户是否已有同内容文件。命中则客户端可跳过上传字节，
     * 但本接口不落库、不返回文件信息；真正落库由 {@code POST /api/files/fast-upload} 完成。</p>
     * <p>入参保持单体契约：查询参数 {@code sha256Hash}、{@code fileSize}。</p>
     *
     * @param sha256Hash 文件 SHA-256（64 位十六进制）
     * @param fileSize   文件大小（字节）
     * @return 判定结果，exists=true 表示可秒传
     */
    @PostMapping("/check")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<FileCheckVO> checkFiles(@RequestParam(required = false) String sha256Hash,
                                          @RequestParam(required = false) Long fileSize) {
        FileCheck fileCheck = new FileCheck();
        fileCheck.setSha256Hash(sha256Hash);
        fileCheck.setFileSize(fileSize);
        return fileFastUploadService.checkFilesHash(fileCheck);
    }

    /**
     * 文件秒传执行（落库阶段）
     * <p>秒传链路的后半段。客户端先用 {@code /api/files/check} 判定，命中后再调本接口在目标目录落库，
     * 全程不传输文件字节。服务端会重新按 sha256 + fileSize 确认物理对象存在，并保证幂等——
     * 目标目录已有同内容同名记录时返回原记录，不重复插入。</p>
     *
     * @param request 秒传请求体（fileName / fileSize / sha256Hash / parentId），JSON
     * @return 落库结果，{@code data.fileId} 为文件记录ID
     */
    @PostMapping("/fast-upload")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 2.0, timeout = 1000)
    public Result<FastUploadVO> fastUpload(@RequestBody FastUploadRequest request) {
        return fileFastUploadService.fastUpload(request);
    }
}
