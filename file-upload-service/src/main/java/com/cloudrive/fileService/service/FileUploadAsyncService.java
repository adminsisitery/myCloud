package com.cloudrive.fileService.service;

import java.io.File;

/**
 * 普通文件异步上传服务
 * <p>在专用线程池中把本地临时文件上传到对象存储并写入文件台账，
 * 全程更新 Redis 里的上传任务进度；失败时归还预占空间。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileUploadAsyncService {

    /**
     * 异步上传本地临时文件
     *
     * @param fileType         文件 MIME 类型
     * @param tmpFile          已落盘的本地临时文件
     * @param originalFilename 原始文件名
     * @param fileSize         文件大小（字节）
     * @param parentId         目标父目录ID
     * @param taskId           上传任务ID
     * @param currentUserId    当前用户ID
     */
    void asyncUploadFile(String fileType, File tmpFile, String originalFilename, long fileSize,
                         Long parentId, String taskId, Long currentUserId);
}
