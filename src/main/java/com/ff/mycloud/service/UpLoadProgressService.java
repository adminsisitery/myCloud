package com.ff.mycloud.service;

import com.ff.mycloud.enums.UploadTaskStatus;
import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.UploadProgressRedis;
import org.springframework.stereotype.Service;

@Service
public class UpLoadProgressService {

    private final UploadProgressRedis uploadProgressRedis;

    public UpLoadProgressService(UploadProgressRedis uploadProgressRedis) {
        this.uploadProgressRedis = uploadProgressRedis;
    }

    public void createUploadTask(String taskId, String originalFilename, long size) {
        uploadProgressRedis.createTask(new UploadTask(taskId, originalFilename, size));
    }

    public void markUploading(String taskId) {
        uploadProgressRedis.updateTask(taskId, task -> {
            if (task.getStatus() == UploadTaskStatus.PENDING) {
                task.setStatus(UploadTaskStatus.UPLOADING);
            }
            return null;
        });
    }

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

    public boolean isCancelled(String taskId) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        return task != null && task.getStatus() == UploadTaskStatus.CANCELLED;
    }

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

    public UploadTask getTask(String taskId) {
        return uploadProgressRedis.getTask(taskId);
    }

    private boolean isTerminal(UploadTaskStatus status) {
        return status == UploadTaskStatus.SUCCESS
                || status == UploadTaskStatus.FAILED
                || status == UploadTaskStatus.CANCELLED;
    }
}
