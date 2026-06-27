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
 * 上传进度查询控制器
 * <p>
 * 提供文件上传任务的进度查询和取消功能。
 * 上传任务在传输中（UPLOADING）或等待中（PENDING）状态下可以被取消。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/3 18:37
 * @version 1.0
 */

@RestController
@RequestMapping("/api/upload-progress")
public class UploadProgressController {

    private final UpLoadProgressService upLoadProgressService;
    private final UploadProgressMapper uploadProgressMapper;

    /**
     * 构造器注入上传进度服务和进度映射器
     *
     * @param upLoadProgressService 上传进度服务
     * @param uploadProgressMapper  上传进度实体到 VO 的映射器
     */
    @Autowired
    public UploadProgressController(UpLoadProgressService upLoadProgressService, UploadProgressMapper uploadProgressMapper) {
        this.upLoadProgressService = upLoadProgressService;
        this.uploadProgressMapper = uploadProgressMapper;
    }


    /**
     * 查询上传任务进度
     * <p>
     * 根据任务ID查询上传任务的当前状态和进度信息。
     * </p>
     *
     * @param taskId 上传任务ID
     * @return 上传进度 VO，如果任务不存在则返回错误
     */
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

    /**
     * 取消上传任务
     * <p>
     * 仅当任务状态为传输中（UPLOADING）或等待中（PENDING）时才能取消。
     * 取消操作通过在服务层设置逻辑取消标记实现。
     * </p>
     *
     * @param taskId 上传任务ID
     * @return 取消结果
     */
    @GetMapping("/{taskId}/cancel")
    public Result<String> cancelUploadProgress(@PathVariable String taskId) {
        UploadTask task = upLoadProgressService.getTask(taskId);
        if (task == null) {
            return Result.error("上传任务不存在");
        }
        // 只有传输中或等待中的任务才能被取消
        if (task.getStatus().equals(UploadTaskStatus.UPLOADING)||task.getStatus().equals(UploadTaskStatus.PENDING)){
            // 设置逻辑取消标记
            upLoadProgressService.cancelUploadTask(taskId, "任务被取消");
            return Result.success("取消成功");
        }else{
            return Result.success("取消失败");
        }

    }
}
