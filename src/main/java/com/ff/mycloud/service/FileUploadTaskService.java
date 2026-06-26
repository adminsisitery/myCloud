package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadTaskService {
    Result<String> uploadWithProgress(MultipartFile file, Long parentId);
}
