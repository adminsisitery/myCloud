package com.ff.mycloud.service;

import com.ff.mycloud.model.vo.FileListVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {
   String uploadFile(MultipartFile file,Long parentId);

    List<FileListVO> listFiles(Long parentId);
}
