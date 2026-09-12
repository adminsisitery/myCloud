package com.ff.mycloud.service;

import com.ff.mycloud.model.entity.FolderDownloadTask;
import com.ff.mycloud.model.vo.FolderDownloadTaskVO;

public interface FolderDownloadTaskService {
    FolderDownloadTaskVO create(Long folderId);
    FolderDownloadTaskVO getOwnedTask(String taskId);
    FolderDownloadTask getDownloadableTask(String taskId);
    void cancel(String taskId);
    void process(String taskId);
    void cleanupExpired();
}
