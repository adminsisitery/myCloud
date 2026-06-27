package com.ff.mycloud.service;

import java.io.File;

/**
 * 文件异步上传服务接口
 * <p>提供异步文件上传功能，使用Spring {@code @Async}线程池执行上传，
 * 支持上传进度更新、取消检测、失败回滚（空间释放和临时文件清理）。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
public interface FileUploadAsyncService {

    /**
     * 异步执行文件上传
     *
     * @param fileType         文件MIME类型
     * @param tmpFile          临时文件对象
     * @param originalFilename 原始文件名
     * @param fileSize         文件大小（字节）
     * @param parentId         父目录ID
     * @param taskId           上传任务ID（用于进度跟踪和取消检测）
     * @param currentUserId    当前用户ID
     */
    void asyncUploadFile(String fileType,
                         File tmpFile,
                         String originalFilename,
                         long fileSize,
                         Long parentId,
                         String taskId,
                         Long currentUserId);
}
