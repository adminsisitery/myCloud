package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传任务服务接口
 * <p>提供带进度跟踪的文件上传功能，包括空间检查、临时文件创建、异步上传提交等。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
public interface FileUploadTaskService {
    /**
     * 上传文件并返回进度跟踪任务ID
     * <p>创建上传任务、检查用户空间、生成临时文件后异步上传。</p>
     *
     * @param file     上传的文件
     * @param parentId 父目录ID
     * @return 操作结果，成功时data字段包含任务ID（用于进度查询）
     */
    Result<String> uploadWithProgress(MultipartFile file, Long parentId);
}
