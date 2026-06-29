package com.ff.mycloud.Controller;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.entity.ChunkFile;
import com.ff.mycloud.model.vo.ChunkCompleteVO;
import com.ff.mycloud.model.vo.ChunkPartVO;
import com.ff.mycloud.model.vo.ChunkProgressVO;
import com.ff.mycloud.model.vo.ChunkVO;
import com.ff.mycloud.service.ChunkUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chunk")
public class BigFileController {

    private final ChunkUploadService chunkUploadService;

    public BigFileController(ChunkUploadService chunkUploadService) {
        this.chunkUploadService = chunkUploadService;
    }

    /**
     * 初始化分片上传
     * <p>前端检测到大文件（&gt;500MB）时主动调用，预占空间并创建OSS MultipartUpload。</p>
     *
     * @param chunkFile 分片上传请求参数
     * @return 包含自定义uploadId和OSS uploadId的结果
     */
    @PostMapping("/init")
    public Result<ChunkVO> init(@RequestBody ChunkFile chunkFile) {
        return chunkUploadService.initUpload(chunkFile);
    }

    /**
     * 上传单个分片
     *
     * @param uploadId   初始化返回的uploadId
     * @param partNumber 分片序号（从1开始）
     * @param chunk      分片文件数据
     * @return 上传结果
     */
    @PostMapping("/upload")
    public Result<ChunkPartVO> upload(@RequestParam("uploadId") String uploadId,
                                       @RequestParam("partNumber") Integer partNumber,
                                       @RequestParam("chunk") MultipartFile chunk) {
        return chunkUploadService.uploadPart(uploadId, partNumber, chunk);
    }

    /**
     * 合并全部分片，完成上传
     *  /api/chunk/complete
     * @param uploadId 初始化返回的uploadId
     * @return 合并后的文件信息
     */
    @PostMapping("/complete")
    public Result<ChunkCompleteVO> complete(@RequestParam("uploadId") String uploadId) {
        return chunkUploadService.completeChunk(uploadId);
    }

    /**
     * 查询分片上传进度（断点续传用）
     *
     * @param uploadId 初始化返回的uploadId
     * @return 已完成和未完成的分片列表
     */
    @GetMapping("/progress/{uploadId}")
    public Result<ChunkProgressVO> progress(@PathVariable("uploadId") String uploadId) {
        return chunkUploadService.getMissingList(uploadId);
    }

    /**
     * 取消分片上传
     *
     * @param uploadId 初始化返回的uploadId
     * @return 取消结果
     */
    @DeleteMapping("/cancel/{uploadId}")
    public Result<String> cancel(@PathVariable("uploadId") String uploadId) {
        return chunkUploadService.cancelUpload(uploadId);
    }
}
