package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.dto.DirectUploadInitDTO;
import com.ff.mycloud.model.vo.ChunkCompleteVO;
import com.ff.mycloud.model.vo.DirectUploadInitVO;
import com.ff.mycloud.service.DirectUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-upload")
public class DirectUploadController {
    private final DirectUploadService service;

    public DirectUploadController(DirectUploadService service) {
        this.service = service;
    }

    @PostMapping("/init")
    @RateLimit(dimensions = {RateLimit.Dimension.USER, RateLimit.Dimension.IP}, permitsPerSecond = 3.0, timeout = 1000)
    public Result<DirectUploadInitVO> init(@Valid @RequestBody DirectUploadInitDTO request) {
        return Result.success(service.init(request));
    }

    @PostMapping("/{taskId}/complete")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 1000)
    public Result<ChunkCompleteVO> complete(@PathVariable String taskId) {
        return Result.success(service.complete(taskId));
    }

    @DeleteMapping("/{taskId}")
    @RateLimit(dimensions = {RateLimit.Dimension.USER}, permitsPerSecond = 5.0, timeout = 1000)
    public Result<Void> cancel(@PathVariable String taskId) {
        service.cancel(taskId);
        return Result.success();
    }
}
