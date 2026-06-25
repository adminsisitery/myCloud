package com.ff.mycloud.service;

import java.io.File;

public interface FileUploadAsyncService {

    void asyncUploadFile(String fileType,
                         File tmpFile,
                         String originalFilename,
                         long fileSize,
                         Long parentId,
                         String taskId,
                         Long currentUserId);
}
