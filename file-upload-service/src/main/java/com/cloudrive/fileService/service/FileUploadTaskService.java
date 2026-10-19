package com.cloudrive.fileService.service;

import com.cloudrive.pan_common.result.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 普通文件上传任务服务
 * <p>同步创建上传任务（占位、预占空间、落临时文件），真正的上传动作交给
 * {@link FileUploadAsyncService} 异步执行，前端通过上传任务ID轮询进度。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileUploadTaskService {

    /**
     * 创建普通文件上传任务并提交异步上传
     *
     * @param file     上传的文件
     * @param parentId 目标父目录ID，0或null表示根目录
     * @return 上传任务ID（前端用它查询进度）
     */
    Result<String> uploadWithProgress(MultipartFile file, Long parentId);
}
