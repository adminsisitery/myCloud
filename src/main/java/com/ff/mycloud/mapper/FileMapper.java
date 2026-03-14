package com.ff.mycloud.mapper;

import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FileListVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.web.multipart.MultipartFile;

@Mapper(componentModel = "spring")
public interface FileMapper {

    // FileInfo -> FileListVO
    @Mapping(target = "id", source = "id")
    @Mapping(target = "filename", source = "filename")
    @Mapping(target = "originalFilename", source = "originalFilename")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "fileType", source = "fileType")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "isFolder", source = "isFolder")
    @Mapping(target = "fileSuffix",source = "fileSuffix")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    FileListVO toFileListVO(FileInfo fileInfo);

    // 正常上传
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", source = "file.originalFilename")
    @Mapping(target = "originalFilename", source = "file.originalFilename")
    @Mapping(target = "path", source = "filePath")
    @Mapping(target = "fileSize", source = "file.size")
    @Mapping(target = "fileType", source = "file.contentType")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "isFolder", constant = "false")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "localFilePath",source = "localFilePath")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "sha256Hash", expression = "java(com.ff.mycloud.common.util.FileHashUtil.calculateSHA256(file))")
    FileInfo toFileInfo(MultipartFile file, String filePath, Long userId, Long parentId, String localFilePath);

    // 秒传
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", source = "filenames")
    @Mapping(target = "originalFilename", source = "filename")
    @Mapping(target = "path", source = "existingFile.path")
    @Mapping(target = "fileSize", source = "existingFile.fileSize")
    @Mapping(target = "fileType", source = "existingFile.fileType")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "isFolder", constant = "false")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "sha256Hash", source = "sha256Hash")
    FileInfo toFileInfoForFastUpload(String filename, String filenames, FileInfo existingFile, Long userId, Long parentId, String sha256Hash);

    // 本地路径上传
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", source = "filename")
    @Mapping(target = "originalFilename", source = "originalFilename")
    @Mapping(target = "path", source = "filePath")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "fileType", source = "fileType")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "isFolder", constant = "false")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "localFilePath",source = "localFilePath")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "sha256Hash", source = "sha256Hash")
    @Mapping(target = "fileSuffix",source = "suffix")
    FileInfo toFileInfoFromPath(String filename, String filePath, Long fileSize, Long userId, Long parentId, String sha256Hash, String suffix, String fileType, String originalFilename, String localFilePath);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", source = "folderName")
    @Mapping(target = "originalFilename", source = "folderName")
    @Mapping(target = "path", constant = "/")
    @Mapping(target = "fileSize", constant = "0L")
    @Mapping(target = "fileType", constant = "folder")
    @Mapping(target = "userId", source = "currentUserId")
    @Mapping(target = "isFolder", source = "isFolder")
    @Mapping(target = "isDeleted", source = "isDeleted")
    @Mapping(target = "localFilePath",source = "localFilePath")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    FileInfo toFileInfoFromFolder(
            String folderName,
            boolean isFolder,
            boolean isDeleted,
            Long parentId,
            Long currentUserId,
            String localFilePath);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "filename", source = "fileCheck.fileName")
    @Mapping(target = "originalFilename", source = "fileCheck.fileName")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "fileSize", source = "fileCheck.fileSize")
    @Mapping(target = "fileType", source = "fileType")
    @Mapping(target = "userId", source = "currentUserId")
    @Mapping(target = "isFolder", constant = "false")
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "localFilePath",source = "localFilePath")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    FileInfo toFileInfoFromFileCheck(FileCheck fileCheck, String path, Long currentUserId, String localFilePath, String fileType);
}