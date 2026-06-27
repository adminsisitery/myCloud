package com.ff.mycloud.service;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.UploadProgressRedis;
import org.springframework.stereotype.Service;

/**
 * 上传进度服务
 * <p>管理文件上传任务的全生命周期，包括创建、进度更新、完成、取消等功能，
 * 基于Redis存储上传任务的实时状态。</p>
 *
 * @author 飞飞
 * @date 2026/2/28
 */
@Service
public class UpLoadProgressService {

    private final UploadProgressRedis uploadProgressRedis;

    public UpLoadProgressService(UploadProgressRedis uploadProgressRedis) {
        this.uploadProgressRedis = uploadProgressRedis;
    }

    /**
     * 创建上传任务
     *
     * @param taskId           任务唯一标识
     * @param originalFilename 原始文件名
     * @param size             文件大小（字节）
     */
    public void createUploadTask(String taskId, String originalFilename, long size) {
        uploadProgressRedis.createTask(new UploadTask(taskId, originalFilename, size));
    }

    /**
     * 标记任务为上传中状态（从PENDING转为UPLOADING）
     *
     * @param taskId 任务ID
     */
    public void markUploading(String taskId) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (task.getStatus() == UploadTaskStatus.PENDING) {
                task.setStatus(UploadTaskStatus.UPLOADING);
            }
            return null;
        });
    }

    /**
     * 更新上传进度（指定已传输字节和总大小）
     *
     * @param taskId           任务ID
     * @param bytesTransferred 已传输字节数
     * @param totalSize        文件总大小（字节）
     */
    public void updateProgress(String taskId, long bytesTransferred, long totalSize) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (isTerminal(task.getStatus())) {
                return null;
            }
            task.setStatus(UploadTaskStatus.UPLOADING);
            task.setBytesTransferred(bytesTransferred);
            task.setTotalSize(totalSize);
            if (totalSize > 0) {
                double percentage = (double) bytesTransferred / totalSize * 100;
                task.setProgress(Math.min(100, percentage));
            }
            return null;
        });
    }

    /**
     * 更新已传输字节数（使用任务已有的总大小计算进度）
     *
     * @param taskId           任务ID
     * @param bytesTransferred 已传输字节数
     */
    public void updateBytesTransferred(String taskId, long bytesTransferred) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (isTerminal(task.getStatus())) {
                return null;
            }
            task.setStatus(UploadTaskStatus.UPLOADING);
            task.setBytesTransferred(bytesTransferred);
            if (task.getTotalSize() > 0) {
                double percentage = (double) bytesTransferred / task.getTotalSize() * 100;
                task.setProgress(Math.min(100, percentage));
            }
            return null;
        });
    }

    /**
     * 完成上传任务
     *
     * @param taskId  任务ID
     * @param success 是否成功
     * @param message 结果描述信息
     */
    public void completeUploadTask(String taskId, boolean success, String message) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (isTerminal(task.getStatus())) {
                return null;
            }
            task.setSuccess(success);
            task.setCompleted(true);
            task.setMessage(message);
            task.setStatus(success ? UploadTaskStatus.SUCCESS : UploadTaskStatus.FAILED);
            if (success) {
                task.setBytesTransferred(task.getTotalSize());
                task.setProgress(100);
            }
            return null;
        });
    }

    /**
     * 取消上传任务
     *
     * @param taskId  任务ID
     * @param message 取消原因
     */
    public void cancelUploadTask(String taskId, String message) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (isTerminal(task.getStatus())) {
                return null;
            }
            task.setStatus(UploadTaskStatus.CANCELLED);
            task.setCompleted(true);
            task.setSuccess(false);
            task.setMessage(message);
            return null;
        });
    }

    /**
     * 判断任务是否已被取消
     *
     * @param taskId 任务ID
     * @return true表示任务已取消
     */
    public boolean isCancelled(String taskId) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        return task != null && task.getStatus() == UploadTaskStatus.CANCELLED;
    }

    /**
     * 尝试标记空间已释放（原子操作，防重复释放）
     *
     * @param taskId 任务ID
     * @return true表示本次成功标记（首次），false表示已释放过
     */
    public boolean tryMarkSpaceReleased(String taskId) {
        Boolean marked = uploadProgressRedis.updateTask(taskId, task -> {
            if (task.isSpaceReleased()) {
                return false;
            }
            task.setSpaceReleased(true);
            return true;
        });
        return Boolean.TRUE.equals(marked);
    }

    /**
     * 获取上传任务详情
     *
     * @param taskId 任务ID
     * @return 上传任务实体，不存在则返回null
     */
    public UploadTask getTask(String taskId) {
        return uploadProgressRedis.getTask(taskId);
    }

    /**
     * 判断状态是否为终态（成功、失败、取消）
     *
     * @param status 上传任务状态
     * @return true表示已处于终态
     */
    private boolean isTerminal(UploadTaskStatus status) {
        return status == UploadTaskStatus.SUCCESS
                || status == UploadTaskStatus.FAILED
                || status == UploadTaskStatus.CANCELLED;
    }
}
