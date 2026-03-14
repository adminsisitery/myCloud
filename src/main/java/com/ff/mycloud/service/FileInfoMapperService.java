package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.FileInfo;

import java.time.LocalDateTime;
import java.util.List;

public interface FileInfoMapperService extends IService<FileInfo> {
    List<FileInfo> findByHashAndUserId(String hash, Long userId);

    List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId);

    boolean UpdateFileNameByFileId(Long fileId, String newFilename, LocalDateTime now);

    Long countFiles(String sha256Hash);

    List<FileInfo> findBykeyWord(String keyword, Long currentUserId);

    FileInfo findByIdAndUserId(Long fileId, Long userId);

    Boolean existsFolder(Long parentId, String folderName, Long currentUserId);

    Boolean existsParentId(Long parentId, Long currentUserId);

    void getFileTree(Long fileId);

    public List<FileInfo> getSubTree(String localFilePath, Long userId) ;

    List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId);
}
