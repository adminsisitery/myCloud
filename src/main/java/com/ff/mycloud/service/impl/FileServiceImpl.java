package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.util.FileHashUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.entity.User;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.FileService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.StorageServiceFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/14 13:21
 * @version 1.0
 */

@Service
public class FileServiceImpl implements FileService {

    private final UserContext userContext;
    StorageServiceFactory storageServiceFactory;
    FileMapper fileMapper;
    FileInfoMapperService fileInfoMapperService;

    public FileServiceImpl(StorageServiceFactory storageServiceFactory, FileMapper fileMapper, FileInfoMapperService fileInfoMapperService, UserContext userContext) {
        this.storageServiceFactory = storageServiceFactory;
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.userContext = userContext;
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
        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
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

        // 如果有任务ID，则进行进度跟踪
//        if (taskId != null) {
//            // 模拟上传进度（秒传情况下直接完成）
//            uploadProgressService.updateProgress(taskId, fileSize, fileSize);
//            uploadProgressService.completeUploadTask(taskId, true, "文件秒传成功");
//        }

        // 保存新的文件记录
//        return fileInfoRepository.save(newFileInfo);
        fileInfoMapperService.save(newFileInfo);
        return newFileInfo;
    }

}
