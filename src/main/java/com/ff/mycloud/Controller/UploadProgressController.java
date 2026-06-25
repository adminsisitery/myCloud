package com.ff.mycloud.Controller;

import com.ff.mycloud.common.annotation.RateLimit;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.mapper.UploadProgressMapper;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.model.vo.UploadProgressVO;
import com.ff.mycloud.service.UpLoadProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/3 18:37
 * @version 1.0
 */

@RestController
@RequestMapping("/api/upload-progress")
public class UploadProgressController {

    private final UpLoadProgressService upLoadProgressService;
    private final UploadProgressMapper uploadProgressMapper;

    @Autowired
    public UploadProgressController(UpLoadProgressService upLoadProgressService, UploadProgressMapper uploadProgressMapper) {
        this.upLoadProgressService = upLoadProgressService;
        this.uploadProgressMapper = uploadProgressMapper;
    }


    @GetMapping("/{taskId}")
    @RateLimit(dimensions = { RateLimit.Dimension.USER, RateLimit.Dimension.IP }, permitsPerSecond = 10.0, timeout = 500)
    public Result<UploadProgressVO> getUploadProgress(@PathVariable String taskId) {
        UploadTask task = upLoadProgressService.getTask(taskId);
        if (task == null) {
            return Result.error("上传任务不存在");
        }
        UploadProgressVO uploadProgressVO = uploadProgressMapper.toUploadProgressVO(task);
        return Result.success(uploadProgressVO);
    }

    // 取消上传
    @GetMapping("/{taskId}/cancel")
    public Result<String> cancelUploadProgress(@PathVariable String taskId) {
        UploadTask task = upLoadProgressService.getTask(taskId);
        if (task == null) {
            return Result.error("上传任务不存在");
        }
        //状态必须是传输中和传输前
        if (task.getStatus().equals(UploadTaskStatus.UPLOADING)||task.getStatus().equals(UploadTaskStatus.PENDING)){
            //设置逻辑取消
            upLoadProgressService.cancelUploadTask(taskId, "任务被取消");
            return Result.success("取消成功");
        }else{
            return Result.success("取消失败");
        }

    }
}
