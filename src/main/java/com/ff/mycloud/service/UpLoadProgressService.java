package com.ff.mycloud.service;

import com.ff.mycloud.model.entity.UploadTask;
import com.ff.mycloud.redis.UploadProgressRedis;
import org.springframework.stereotype.Service;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/3 13:54
 * @version 1.0
 */

@Service
public class UpLoadProgressService {

    UploadProgressRedis uploadProgressRedis;

    public UpLoadProgressService(UploadProgressRedis uploadProgressRedis) {
        this.uploadProgressRedis = uploadProgressRedis;
    }

    public void createUploadTask(String taskId, String originalFilename, long size){
        UploadTask uploadTask = new UploadTask(taskId, originalFilename, size);
        uploadProgressRedis.createTask(uploadTask);
    }
    //设置初始进度为0
    public void updateProgress(String taskId, long i, long size) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            task.setBytesTransferred(i);
            task.setTotalSize(size);
            if (size > 0){
                double percentage = (double)task.getBytesTransferred() / size * 100;
                task.setProgress(Math.min(100,percentage));
            }
            uploadProgressRedis.saveTask(task);
        }
    }

    //累加更新新的传输进度
    public void updateBytesTransferred(String taskId, long addBytesTransferred) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            //计算累加的
            long bytesTransferred = addBytesTransferred;
            task.setBytesTransferred(bytesTransferred);
            if (task.getTotalSize() > 0){
                double per = (double)bytesTransferred / task.getTotalSize() * 100;
                task.setProgress(Math.min(100,per));
            }
            uploadProgressRedis.saveTask(task);
        }
    }

    public void completeUploadTask(String taskId, boolean success, String message) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            task.setSuccess(success);
            task.setCompleted(true);
            task.setMessage(message);
            task.setProgress(100);
            uploadProgressRedis.saveTask(task);
        }

    }

    public UploadTask getTask(String taskId) {
        return uploadProgressRedis.getTask(taskId);
    }
}
