package com.ff.mycloud.service;

import com.ff.mycloud.model.vo.FileListVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public interface FileService {
   String uploadFile(MultipartFile file,Long parentId);

    List<FileListVO> listFiles(Long parentId);

    void uploadFileWithProgressFromPath(String fileType, String absolutePath, String originalFilename, long size, Long parentId, String taskId, Long currentUserId);

    byte[] downloadFile(Long fileId);

    String getFilename(long fileId);

    void renameFile(Long fileId, @NotBlank(message = "新文件名不能为空") @Size(max = 50, message = "新文件名不能超过50个字符") String newFilename);

    void deleteFile(Long fileId);

 List<FileListVO> searchFiles(String keyword);

 byte[] getFileContent(Long id);

 InputStream getFile(String path);

}
