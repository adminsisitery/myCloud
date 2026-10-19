package com.cloudrive.fileManageService.mapper;

import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.model.entity.FileObject;
import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.pan_common.enums.FileObjectStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * 文件领域对象转换器（MapStruct）
 * <p>
 * 负责 FileInfo -> FileListVO、新建文件夹 FileInfo、FileInfo -> FileObject 的字段映射。
 * 与 file-upload-service 中的同名转换器保持字段口径一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Mapper(componentModel = "spring")
public interface FileMapper {

    /**
     * FileInfo 转文件列表项
     *
     * @param fileInfo 文件信息实体
     * @return 文件列表视图对象
     */
    FileListVO toFileListVO(FileInfo fileInfo);

    /**
     * 构造新建文件夹实体
     *
     * @param folderName   文件夹名称
     * @param isFolder     是否文件夹
     * @param isDeleted    是否删除
     * @param parentId     父目录 ID
     * @param currentUserId 当前用户 ID
     * @param localFilePath 目录本地路径
     * @return 待保存的文件夹实体
     */
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "filename", source = "folderName"),
            @Mapping(target = "originalFilename", source = "folderName"),
            @Mapping(target = "path", constant = "/"),
            @Mapping(target = "fileSize", constant = "0L"),
            @Mapping(target = "fileType", constant = "folder"),
            @Mapping(target = "sha256Hash", ignore = true),
            @Mapping(target = "fileSuffix", ignore = true),
            @Mapping(target = "userId", source = "currentUserId"),
            @Mapping(target = "parentId", source = "parentId"),
            @Mapping(target = "isFolder", source = "isFolder"),
            @Mapping(target = "isDeleted", source = "isDeleted"),
            @Mapping(target = "localFilePath", source = "localFilePath"),
            @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())"),
            @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())"),
            @Mapping(target = "deletedAt", ignore = true),
            @Mapping(target = "isStarred", ignore = true),
            @Mapping(target = "starredAt", ignore = true),
            @Mapping(target = "categoryId", ignore = true)
    })
    FileInfo toFileInfoFromFolder(String folderName, Boolean isFolder, Boolean isDeleted,
                                  Long parentId, Long currentUserId, String localFilePath);

    /**
     * FileInfo 转 FileObject（复制文件时增加物理对象引用）
     *
     * @param fileInfo         文件信息实体
     * @param fileObjectStatus 对象状态
     * @return 待创建的文件对象
     */
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "sha256Hash", source = "fileInfo.sha256Hash"),
            @Mapping(target = "fileSize", source = "fileInfo.fileSize"),
            @Mapping(target = "storagePath", source = "fileInfo.path"),
            @Mapping(target = "fileType", source = "fileInfo.fileType"),
            @Mapping(target = "fileSuffix", source = "fileInfo.fileSuffix"),
            @Mapping(target = "status", expression = "java(fileObjectStatus.getCode())"),
            @Mapping(target = "refCount", constant = "1"),
            @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())"),
            @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    })
    FileObject toFileObject(FileInfo fileInfo, FileObjectStatus fileObjectStatus);
}
