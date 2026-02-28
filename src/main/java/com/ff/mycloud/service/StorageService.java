package com.ff.mycloud.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String UploadFile(MultipartFile file, String path);
}
