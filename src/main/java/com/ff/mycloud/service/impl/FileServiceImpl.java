package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.FileHashUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/14 13:21
 * @version 1.0
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final UserContext userContext;
    private final UserMapperServiceImpl userMapperServiceImpl;
    private final UpLoadProgressService upLoadProgressService;
    StorageServiceFactory storageServiceFactory;
    FileMapper fileMapper;
    FileInfoMapperService fileInfoMapperService;

    public FileServiceImpl(StorageServiceFactory storageServiceFactory, FileMapper fileMapper, FileInfoMapperService fileInfoMapperService, UserContext userContext, UserMapperServiceImpl userMapperServiceImpl, UpLoadProgressService upLoadProgressService) {
        this.storageServiceFactory = storageServiceFactory;
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.userContext = userContext;
        this.userMapperServiceImpl = userMapperServiceImpl;
        this.upLoadProgressService = upLoadProgressService;
    }

    @Override
    public String uploadFile(MultipartFile file, Long parentId) {

        //1.计算文件的hash值
        User currentUser = UserContext.getCurrentUser();
        String sha256Hash = FileHashUtil.calculateSHA256(file);
        if (sha256Hash!=null && !sha256Hash.isEmpty()){
            //在数据库中查找是否有这个哈希值对应的文件信息 fileInfo表
//            List<FileInfo> existingFiles = fileInfoRepository.findBySha256HashAndUserIdAndIsDeletedFalse(sha256Hash, currentUser.getId());
            List<FileInfo> existingFiles = fileInfoMapperService.findByHashAndUserId(sha256Hash, currentUser.getId());
            //如果存在秒传
            if (!existingFiles.isEmpty()){
                FileInfo existingFile = existingFiles.get(0);
                FileInfo newFileInfo = handleFastUpload(file.getOriginalFilename(), file.getSize(), existingFile, sha256Hash, parentId, null, currentUser);
                return newFileInfo.getPath();
            }

            //不存在正常上传
            StorageService storageService = storageServiceFactory.getStorageService();
            //得到文件的路径根据parentID 得到父路径 还没有文件名！！！！！
            String uploadPath = getUploadPath(parentId, currentUser);
            //上传并得到路径和文件名
            String filePath = storageService.UploadFile(file, uploadPath);
            //进行fileInfo对象拼装
            FileInfo fileInfo = fileMapper.toFileInfo(file, filePath,currentUser.getId(), parentId);
            //保存到数据库中
//            return fileInfoRepository.save(fileInfo).getPath();
            fileInfoMapperService.save(fileInfo);
            return fileInfo.getPath();
        }
        return null;
    }

    //显示文件列表
    @Override
    public List<FileListVO> listFiles(Long parentId) {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> fileInfos = fileInfoMapperService.findByParentIdAndUserId(currentUserId,parentId);

        for (FileInfo fileInfo : fileInfos) {
            fileInfo.setFilename(fileInfo.getFilename()+"."+fileInfo.getFileSuffix());
        }

        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public void uploadFileWithProgressFromPath(String fileType, String absolutePath, String originalFilename, long size, Long parentId, String taskId, Long currentUserId) {
        //根据用户id获得用户信息
        User currentUser = userMapperServiceImpl.findOneByUserId(currentUserId);

        //从路径获取文件
        File file = new File(absolutePath);
        //检查是不是文件并存在
        if (!file.exists()||!file.isFile()){
            log.info("文件不存在或不是常规文件");
//            uploadProgressService.completeUploadTask(taskId, false, "文件不存在或不是常规文件");
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        //计算文件的SHA-256
        String fileHashSHA256 = FileHashUtil.calculateSHA256(file);
        //查看数据库里是否有文件 是否上传过
        List<FileInfo> byHashAndUserId = fileInfoMapperService.findByHashAndUserId(fileHashSHA256, currentUserId);
        //存在文件 直接秒传
        if (!byHashAndUserId.isEmpty()){
            FileInfo existingFile = byHashAndUserId.get(0);
            handleFastUpload(originalFilename,size,existingFile,fileHashSHA256,parentId,taskId,currentUser);
            return;
        }
        //不存在文件正常上传
        String uploadPath = getUploadPath(parentId, currentUser);
        StorageService storageService = storageServiceFactory.getStorageService();
        String path = storageService.uploadFileWithProgressFromPath(file, uploadPath, taskId, originalFilename, size);



        //新增保存后缀
        String suffix="";
        String fileNameWithoutSuffix="";
        int i = originalFilename.lastIndexOf(".");
        if (i!=-1){
            suffix = originalFilename.substring(i+1);
            fileNameWithoutSuffix = originalFilename.substring(0, i);
        }

        FileInfo fileInfo = fileMapper.toFileInfoFromPath(fileNameWithoutSuffix, path, size, currentUser.getId(), parentId, fileHashSHA256,suffix,fileType,originalFilename);

        fileInfoMapperService.save(fileInfo);
    }

    @Override
    public byte[] downloadFile(Long fileId) {
        //先得到fileInfo
        User currentUser = UserContext.getCurrentUser();
        FileInfo andValidateFile = getAndValidateFile(fileId, currentUser);
        return retrieveFileContent(andValidateFile);
    }

    @Override
    public String getFilename(long fileId) {
        return fileInfoMapperService.getById(fileId).getFilename();
    }

    @Override
    public void renameFile(Long fileId, String newFilename) {

        if (newFilename == null || newFilename.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILENAME);
        }

        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        int dotIndex = newFilename.lastIndexOf(".");
        if (dotIndex != -1) {
            newFilename = newFilename.substring(0, dotIndex);
        }

        fileInfo.setFilename(newFilename);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);
    }

    @Override
    public void deleteFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        //计算具有相同文件的索引个数
        fileInfoMapperService.countFiles(fileInfo.getSha256Hash());
        // 1 先逻辑删除当前记录
        fileInfo.setIsDeleted(true);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);

        // 2 再判断 hash 是否还有引用
        long referenceCount = fileInfoMapperService.countFiles(fileInfo.getSha256Hash());

        if (referenceCount <= 1) {
            StorageService storageService =
                    storageServiceFactory.getStorageService();
            storageService.deleteFile(fileInfo.getPath());
            log.info("Physically deleted file: {}", fileInfo.getPath());
        }

    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        List<FileInfo> userInfoList = fileInfoMapperService.findBykeyWord(keyword,UserContext.getCurrentUserId());
        return userInfoList.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public byte[] getFileContent(Long id) {
        FileInfo byId = fileInfoMapperService.getById(id);
        if (byId==null){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return retrieveFileContent(byId);
    }

    private byte[] retrieveFileContent(FileInfo andValidateFile) {
        ExceptionUtil.throwIf(andValidateFile.getIsFolder(),ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        String filePath = andValidateFile.getPath();
        StorageService storageService = storageServiceFactory.getStorageService();
        return storageService.downloadFile(filePath);
    }

    private FileInfo getAndValidateFile(Long fileId, User currentUser) {
        FileInfo byId = fileInfoMapperService.getById(fileId);
        if (byId==null){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ExceptionUtil.throwIf(!byId.getUserId().equals(currentUser.getId()),ErrorCode.NO_PERMISSION);
        ExceptionUtil.throwIf(byId.getIsDeleted(),ErrorCode.FILE_NOT_FOUND);
        return byId;
    }


    private String getUploadPath(Long parentId, User currentUser) {
        //1.如果没有传入父路径的情况
        String path = CommonConstants.File.FILE_PATH_PREFIX + currentUser.getId();
        if (parentId != null) {
            //传入了父路径的情况
            FileInfo parent = fileInfoMapperService.getById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            path = parent.getPath();
        }
        return path;
    }

//    这个原理就是不用上传文件 在数据库生成一个记录就行 不管用户文甲明是不是一样 只要路径对应就行
    private FileInfo handleFastUpload(String filename, long fileSize, FileInfo existingFile, String sha256Hash, Long parentId, String taskId, User currentUser) {
        // 使用MapStruct创建一个新的文件记录
        FileInfo newFileInfo = fileMapper.toFileInfoForFastUpload(filename, existingFile, currentUser.getId(), parentId, sha256Hash);

//         如果有任务ID，则进行进度跟踪
        if (taskId != null) {
            // 模拟上传进度（秒传情况下直接完成）
            upLoadProgressService.updateProgress(taskId, fileSize, fileSize);
            upLoadProgressService.completeUploadTask(taskId, true, "文件秒传成功");
        }

        // 保存新的文件记录
//        return fileInfoRepository.save(newFileInfo);
        fileInfoMapperService.save(newFileInfo);
        return newFileInfo;
    }

}
