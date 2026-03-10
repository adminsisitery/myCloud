package com.ff.mycloud.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;

public interface StorageService {
    String UploadFile(MultipartFile file, String path);

    String uploadFileWithProgressFromPath(File file, String uploadPath, String taskId, String originalFilename, long size);

    byte[] downloadFile(String filePath);

    void deleteFile(String path);

    InputStream getFile(String path);

}
