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
        createUploadTask(taskId, originalFilename, size, null);
    }

    /**
     * 创建上传任务（带用户ID）
     * <p>userId 会随任务一起写入 Redis。异步线程若已消失（JVM 重启、线程池拒绝等），
     * 超时扫描任务只能从任务快照里取 userId 才能代为归还预占空间。</p>
     *
     * @param taskId           任务唯一标识
     * @param originalFilename 原始文件名
     * @param size             文件大小（字节）
     * @param userId           任务所属用户ID
     */
    public void createUploadTask(String taskId, String originalFilename, long size, Long userId) {
        uploadProgressRedis.createTask(new UploadTask(taskId, originalFilename, size, userId));
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
     * 回滚「空间已释放」标记（归还动作失败时调用）
     * <p>归还动作是在锁外执行的：tryMarkSpaceReleased 返回 true 的那一刻标记已落库。
     * 若随后的 releaseSpace 失败，标记必须回滚，否则后续每一次重试都会被
     * tryMarkSpaceReleased 挡回 false —— 空间永久漏还，且不会有任何告警。</p>
     *
     * @param taskId 任务ID
     */
    public void unmarkSpaceReleased(String taskId) {
        uploadProgressRedis.updateTask(taskId, task -> {
            task.setSpaceReleased(false);
            return null;
        });
    }

    /**
     * 标记该任务已成功预占额度
     * <p>在 reserveSpace 返回成功之后调用。标记的意义是让「还没结清的预占」这件事
     * 变成任务快照里的一个显式字段，不再需要从 status 去推断 —— 因为取消会把状态
     * 置成终态 CANCELLED，而释放却依赖异步线程，线程从未执行时状态就失去了表达力。</p>
     *
     * @param taskId 任务ID
     */
    public void markSpaceReserved(String taskId) {
        uploadProgressRedis.updateTask(taskId, task -> {
            task.setSpaceReserved(true);
            return null;
        });
    }

    /**
     * 结清预占：该任务不再有「待归还的额度」
     * <p>两种情况调用：① 文件已落库，预占正式转为真实占用；② 额度已归还。</p>
     * <p>结清之后超时扫描不会再碰它，避免把一笔已经成立的实际占用当成漏还退回去
     * —— 那是资损方向（文件在、额度却退了）。</p>
     *
     * @param taskId 任务ID
     */
    public void clearSpaceReserved(String taskId) {
        uploadProgressRedis.updateTask(taskId, task -> {
            task.setSpaceReserved(false);
            return null;
        });
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
