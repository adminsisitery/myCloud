package com.ff.mycloud.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
   String uploadFile(MultipartFile file,Long parentId);

}
