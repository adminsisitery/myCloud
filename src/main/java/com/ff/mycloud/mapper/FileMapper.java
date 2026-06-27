package com.ff.mycloud.mapper;

import com.ff.mycloud.enums.FileObjectStatus;
import com.ff.mycloud.model.entity.FileCheck;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.FileObject;
import com.ff.mycloud.model.vo.FileListVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.web.multipart.MultipartFile;

/**
 * 功能描述：文件映射器，负责文件相关实体与视图对象之间的转换，支持普通上传、秒传、本地路径上传、文件夹创建等多种场景。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Mapper(componentModel = "spring")
public interface FileMapper {

    /**
     * 功能：将文件信息实体转换为文件列表视图对象。
     *
     * @param fileInfo 文件信息实体
     * @return 文件列表视图对象
     */
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

    /**
     * 功能：将普通上传参数转换为文件信息实体（计算SHA256哈希值）。
     *
     * @param file          上传的MultipartFile文件对象
     * @param filePath      文件存储路径
     * @param userId        用户ID
     * @param parentId      父目录ID
     * @param localFilePath 本地文件路径
     * @return 文件信息实体
     */
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

    /**
     * 功能：将秒传参数转换为文件信息实体（复用已有文件信息）。
     *
     * @param filename     原始文件名
     * @param filenames    存储文件名
     * @param existingFile 已存在的文件信息实体
     * @param userId       用户ID
     * @param parentId     父目录ID
     * @param sha256Hash   文件SHA256哈希值
     * @return 文件信息实体
     */
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

    /**
     * 功能：将本地路径上传参数转换为文件信息实体。
     *
     * @param filename         文件名
     * @param filePath         文件存储路径
     * @param fileSize         文件大小（单位：字节）
     * @param userId           用户ID
     * @param parentId         父目录ID
     * @param sha256Hash       文件SHA256哈希值
     * @param suffix           文件后缀名
     * @param fileType         文件类型
     * @param originalFilename 原始文件名
     * @param localFilePath    本地文件路径
     * @return 文件信息实体
     */
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


    /**
     * 功能：将文件夹创建参数转换为文件信息实体。
     *
     * @param folderName     文件夹名称
     * @param isFolder       是否为文件夹（始终为true）
     * @param isDeleted      是否已删除（始终为false）
     * @param parentId       父目录ID
     * @param currentUserId  当前用户ID
     * @param localFilePath  本地文件路径
     * @return 文件信息实体
     */
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


    /**
     * 功能：将文件校验实体及上传参数转换为文件信息实体。
     *
     * @param fileCheck      文件校验实体（分片校验结果）
     * @param path           文件存储路径
     * @param currentUserId  当前用户ID
     * @param localFilePath  本地文件路径
     * @param fileType       文件类型
     * @return 文件信息实体
     */
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

    /**
     * 功能：将文件信息实体及文件状态转换为文件对象实体（用于底层存储映射）。
     *
     * @param fileInfo 文件信息实体
     * @param status   文件对象状态枚举
     * @return 文件对象实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sha256Hash", source = "fileInfo.sha256Hash")
    @Mapping(target = "fileSize", source = "fileInfo.fileSize")
    @Mapping(target = "storagePath", source = "fileInfo.path")
    @Mapping(target = "fileType", source = "fileInfo.fileType")
    @Mapping(target = "fileSuffix", source = "fileInfo.fileSuffix")
    @Mapping(target = "status", expression = "java(status.getCode())")
    @Mapping(target = "refCount", constant = "1")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    FileObject toFileObject(FileInfo fileInfo, FileObjectStatus status);
}
