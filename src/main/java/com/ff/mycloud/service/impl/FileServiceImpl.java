package com.ff.mycloud.service.impl;

//import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.FileHashUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.enums.FileObjectStatus;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.*;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private final FileObjectMapper fileObjectMapper;
    private final HttpCodeStatusMapper healthHttpCodeStatusMapper;
    StorageServiceFactory storageServiceFactory;
    FileMapper fileMapper;
    FileInfoMapperService fileInfoMapperService;
    UserCenterMapperService userCenterMapperService;
//    FileSearchService fileSearchService;
//    FileSearchRepository searchRepository;

    public FileServiceImpl(StorageServiceFactory storageServiceFactory,
                           UserCenterMapperService userCenterMapperService,
                           FileMapper fileMapper,
                           FileInfoMapperService fileInfoMapperService,
                           UserContext userContext,
                           UserMapperServiceImpl userMapperServiceImpl,
                           UpLoadProgressService upLoadProgressService,
//                           ,FileSearchService fileSearchService,
//                           FileSearchRepository searchRepository
                           FileObjectMapper fileObjectMapper, HttpCodeStatusMapper healthHttpCodeStatusMapper) {
        this.storageServiceFactory = storageServiceFactory;
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.userContext = userContext;
        this.userMapperServiceImpl = userMapperServiceImpl;
        this.upLoadProgressService = upLoadProgressService;
        this.userCenterMapperService = userCenterMapperService;
//        this.fileSearchService = fileSearchService;
//        this.searchRepository = searchRepository;
        this.fileObjectMapper = fileObjectMapper;
        this.healthHttpCodeStatusMapper = healthHttpCodeStatusMapper;
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
            String localFilePath="";
            //计算在文件夹里创建文件的路径
            if (parentId!=null){
                //有父级文件夹
                FileInfo byId = fileInfoMapperService.getById(parentId);
                if(byId!=null) {
                    localFilePath = byId.getLocalFilePath() + file.getOriginalFilename();
                }
            }

            //进行fileInfo对象拼装
            FileInfo fileInfo = fileMapper.toFileInfo(file, filePath,currentUser.getId(), parentId,localFilePath);
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
            if (!fileInfo.getIsFolder()) {
                fileInfo.setFilename(fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
            }
            fileInfo.setFilename(fileInfo.getFilename());
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
        //     uploadProgressService.completeUploadTask(taskId, false, "文件不存在或不是常规文件");
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        //计算文件的SHA-256
        String fileHashSHA256 = FileHashUtil.calculateSHA256(file);
        //查看数据库里是否有文件 是否上传过
        //秒传只允许复用“当前用户已经拥有过的文件”。
        List<FileInfo> byHashAndUserId = fileInfoMapperService.findByHashAndUserId(fileHashSHA256, currentUserId);
        //存在文件 直接秒传
        if (!byHashAndUserId.isEmpty()){
            FileInfo existingFile = byHashAndUserId.get(0);

            String localFilePath="";
            if (parentId!=null){
                FileInfo byId = fileInfoMapperService.getById(parentId);
                if(byId!=null) {
                    localFilePath = byId.getLocalFilePath() + originalFilename;
                }
            }

            existingFile.setLocalFilePath(localFilePath);

            handleFastUpload(originalFilename,size,existingFile,fileHashSHA256,parentId,taskId,currentUser);
            return;
        }
        //不存在文件正常上传
        String uploadPath = getUploadPath(parentId, currentUser);
        StorageService storageService = storageServiceFactory.getStorageService();
        String path = storageService.uploadFileWithProgressFromPath(file, uploadPath, taskId, originalFilename, size,fileType);

        //新增保存后缀
        String suffix="";
        String fileNameWithoutSuffix="";
        int i = originalFilename.lastIndexOf(".");
        if (i!=-1){
            suffix = originalFilename.substring(i+1);
            fileNameWithoutSuffix = originalFilename.substring(0, i);
        }

        String localFilePath="";
        if (parentId!=null){
            FileInfo byId = fileInfoMapperService.getById(parentId);
            if(byId!=null) {
                localFilePath = byId.getLocalFilePath() + originalFilename;
            }
        }

        FileInfo fileInfo = fileMapper.toFileInfoFromPath(fileNameWithoutSuffix, path, size, currentUser.getId(), parentId, fileHashSHA256,suffix,fileType,originalFilename,localFilePath);
        FileObject fileObject = fileMapper.toFileObject(fileInfo, FileObjectStatus.AVAILABLE);
        fileInfoMapperService.save(fileInfo);
        fileObjectMapper.createFileObject(fileObject);//上传完成保存保存object表
        // todo 上传完成保存文件的分片hash信息

        createSample(file,fileObject);
//        //上传完成 同步ES
//        FileDocument fileDocument = new FileDocument();
//        fileDocument.setId(fileInfo.getId());
//        fileDocument.setFileName(fileInfo.getFilename()+ "." + fileInfo.getFileSuffix());
//        fileDocument.setFileType(fileInfo.getFileType());
//        fileDocument.setUserId(currentUserId);
//        searchRepository.save(fileDocument);
    }

    private void createSample(File file, FileObject fileObject) {
        
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
//
//        //直接调用删改名传入id和名字就行
//        if (!fileInfo.getIsFolder()) {
//            fileSearchService.renameById(fileId, fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
//        }
//        fileSearchService.renameById(fileId, newFilename);
    }

    public void deleteSimpleFile(Long fileId) {
        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        //计算具有相同文件的索引个数
        fileInfoMapperService.countFiles(fileInfo.getSha256Hash());
        // 1 先逻辑删除当前记录
        fileInfo.setIsDeleted(true);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);
        //1.5 同时删除减去的空间
        UserCenter byUserId = userCenterMapperService.getByUserId(UserContext.getCurrentUserId());
        Long usedSpace = byUserId.getUsedSpace();
        byUserId.setUsedSpace(usedSpace - fileInfo.getFileSize());
        userCenterMapperService.updateById(byUserId);

        // 2 再判断 hash 是否还有引用
        long referenceCount = fileInfoMapperService.countFiles(fileInfo.getSha256Hash());

        if (referenceCount <= 1) {
            StorageService storageService =
                    storageServiceFactory.getStorageService();
            storageService.deleteFile(fileInfo.getPath());
            log.info("Physically deleted file: {}", fileInfo.getPath());
        }
    }
//    @Override
//    public void deleteFile(Long fileId) {
//        // 1. 空值校验：防止fileId无效导致NPE
//        if (fileId == null) {
//            log.error("删除文件失败：fileId为空");
//            throw new IllegalArgumentException("文件ID不能为空");
//        }
//
//        // 2. 获取根节点并校验
//        FileInfo root = fileInfoMapperService.getById(fileId);
//        if (root == null) {
//            log.error("删除文件失败：未找到ID为{}的文件/文件夹", fileId);
//            throw new RuntimeException("文件/文件夹不存在");
//        }
//
//        // 3. 权限校验：确保操作的是当前用户的文件
//        Long currentUserId = UserContext.getCurrentUserId();
//        if (!root.getUserId().equals(currentUserId)) {
//            log.error("删除文件失败：用户{}无权限删除用户{}的文件(ID:{})",
//                    currentUserId, root.getUserId(), fileId);
//            throw new RuntimeException("无权限删除该文件/文件夹");
//        }
//
//        // 4. 修复路径匹配：统一处理路径结尾的/，避免双/或无/导致匹配失败
//        String rootFilePath = root.getLocalFilePath();
//        // 标准化路径：确保结尾无/，后续拼接统一加/
//        if (rootFilePath.endsWith("/")) {
//            rootFilePath = rootFilePath.substring(0, rootFilePath.length() - 1);
//        }
//        log.info("开始删除文件/文件夹：ID={}, 路径={}, 用户ID={}", fileId, rootFilePath, currentUserId);
//
//        // 5. 获取待删除的所有子节点
//        List<FileInfo> files = fileInfoMapperService.getSubTree(rootFilePath, currentUserId);
//        log.info("查询到待删除的文件/文件夹数量：{}", files.size());
//
//        long totalSize = 0;
//
//        // 6. 批量标记删除 + 统计空间 + 物理删除（按需）
//        for (FileInfo file : files) {
//            log.debug("处理文件/文件夹：ID={}, 路径={}, 是否文件夹={}",
//                    file.getId(), file.getLocalFilePath(), file.getIsFolder());
//
//            // 统计文件大小（文件夹不统计）
//            if (!file.getIsFolder()) {
//                totalSize += file.getFileSize();
//            }
//
//            // 标记为已删除
//            file.setIsDeleted(true);
//            file.setUpdatedAt(LocalDateTime.now());
//            fileInfoMapperService.updateById(file);
//
//            // 秒传引用判断：仅文件需要处理物理删除
//            if (!file.getIsFolder()) {
//                // 关键：countFiles需要统计所有状态的引用数，否则会误判
//                long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
//                log.debug("文件{}的SHA256引用数：{}", file.getLocalFilePath(), referenceCount);
//
//                if (referenceCount <= 1) {
//                    StorageService storageService = storageServiceFactory.getStorageService();
//                    // 物理删除文件（增加异常捕获，避免单个文件删除失败导致整体中断）
//                    try {
//                        storageService.deleteFile(file.getPath());
//                        log.info("物理删除文件成功：{}", file.getPath());
//                    } catch (Exception e) {
//                        log.error("物理删除文件失败：{}", file.getPath(), e);
//                        // 可选：抛出异常中断，或记录后继续
//                        // throw new RuntimeException("物理删除文件失败", e);
//                    }
//                }
//            }
//        }
//
//        // 7. 更新用户空间（校验用户存在）
//        UserCenter user = userCenterMapperService.getByUserId(currentUserId);
//        if (user == null) {
//            log.error("更新用户空间失败：未找到用户{}", currentUserId);
//            throw new RuntimeException("用户不存在");
//        }
//
//        // 防止空间变为负数
//        long newUsedSpace = Math.max(0, user.getUsedSpace() - totalSize);
//        user.setUsedSpace(newUsedSpace);
//        userCenterMapperService.updateById(user);
//        log.info("删除完成：共释放空间{}字节，用户{}当前已用空间{}",
//                totalSize, currentUserId, newUsedSpace);
//    }

    @Override
    public void deleteFile(Long fileId) {
        // 1. 空值校验
        if (fileId == null) {
            log.error("删除文件失败：fileId为空");
            throw new IllegalArgumentException("文件ID不能为空");
        }

        // 2. 获取根节点并校验
        FileInfo root = fileInfoMapperService.getById(fileId);
        if (root == null) {
            log.error("删除文件失败：未找到ID为{}的文件/文件夹", fileId);
            throw new RuntimeException("文件/文件夹不存在");
        }

        // 3. 权限校验
        Long currentUserId = UserContext.getCurrentUserId();
        if (!root.getUserId().equals(currentUserId)) {
            log.error("删除文件失败：用户{}无权限删除用户{}的文件(ID:{})",
                    currentUserId, root.getUserId(), fileId);
            throw new RuntimeException("无权限删除该文件/文件夹");
        }

        // 4. 核心分支：先判断是文件还是文件夹
        List<FileInfo> filesToDelete;
        if (root.getIsFolder()) {
            // ========== 分支1：是文件夹 ==========
            String folderPath = root.getLocalFilePath();

            // 防止空指针（历史脏数据保护）
            if (folderPath == null || folderPath.isEmpty()) {
                log.error("删除文件夹失败：路径为空，ID={}", fileId);
                throw new RuntimeException("文件夹路径异常");
            }

            // 标准化路径：去掉结尾的 /
            if (folderPath.endsWith("/")) {
                folderPath = folderPath.substring(0, folderPath.length() - 1);
            }

            log.info("开始删除文件夹：ID={}, 路径={}, 用户ID={}", fileId, folderPath, currentUserId);

            // 获取所有子节点（包括文件夹自己）
            filesToDelete = fileInfoMapperService.getSubTree(folderPath, currentUserId);

        } else {
            // ========== 分支2：是单文件 ==========
            log.info("开始删除文件：ID={}, 路径={}, 用户ID={}", fileId, root.getLocalFilePath(), currentUserId);

            // 单文件直接只处理自己，不用查子树
            filesToDelete = Collections.singletonList(root);
        }

        log.info("待处理的文件/文件夹总数：{}", filesToDelete.size());
        long totalSize = 0;

        // 5. 统一处理删除逻辑（文件和文件夹共用）
        for (FileInfo file : filesToDelete) {
            log.debug("处理：ID={}, 路径={}, 类型={}",
                    file.getId(), file.getLocalFilePath(), file.getIsFolder() ? "文件夹" : "文件");

            // 统计文件大小（仅文件统计）
            if (!file.getIsFolder()) {
                totalSize += file.getFileSize();
            }

            // 标记为已删除
            file.setIsDeleted(true);
            file.setUpdatedAt(LocalDateTime.now());
            fileInfoMapperService.updateById(file);
//            //直接调用删除 ES删掉就行
//            fileSearchService.del(fileId);

            // 物理删除（仅文件需要，且仅当无引用时）
            if (!file.getIsFolder()) {
                long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
                log.debug("文件{}引用数：{}", file.getLocalFilePath(), referenceCount);

                if (referenceCount <= 1) {
                    StorageService storageService = storageServiceFactory.getStorageService();
                    try {
                        storageService.deleteFile(file.getPath());
                        log.info("物理删除成功：{}", file.getPath());
                    } catch (Exception e) {
                        log.error("物理删除失败：{}", file.getPath(), e);
                    }
                }
            }
        }
        // todo 更新用户空间增加mq过渡
        // 7. 更新用户空间（校验用户存在）
        UserCenter user = userCenterMapperService.getByUserId(currentUserId);
        if (user == null) {
            log.error("更新用户空间失败：未找到用户{}", currentUserId);
            throw new RuntimeException("用户不存在");
        }

        // 防止空间变为负数
        long newUsedSpace = Math.max(0, user.getUsedSpace() - totalSize);
        user.setUsedSpace(newUsedSpace);
        userCenterMapperService.updateById(user);
        log.info("删除完成：共释放空间{}字节，用户{}当前已用空间{}",
                totalSize, currentUserId, newUsedSpace);
    }

//    @Override
//    public List<FileListVO> searchFiles(String keyword) {
//        List<FileInfo> fileInfos = fileSearchService.searchFile(keyword);
//        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
//    }
    @Override
    public List<FileListVO> searchFiles(String keyword) {
        List<FileInfo> fileInfos = fileInfoMapperService.findBykeyWord(keyword, UserContext.getCurrentUserId());
        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public byte[] getFileContent(Long id) {
        FileInfo byId = fileInfoMapperService.getById(id);
        if (byId==null){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return retrieveFileContent(byId);
    }

    @Override
    public InputStream getFile(String path) {
        StorageService storageService = storageServiceFactory.getStorageService();
        return storageService.getFile(path);
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

        return path;
    }

//    这个原理就是不用上传文件 在数据库生成一个记录就行 不管用户文甲明是不是一样 只要路径对应就行
    private FileInfo handleFastUpload(String filename, long fileSize, FileInfo existingFile, String sha256Hash, Long parentId, String taskId, User currentUser) {
        // 使用MapStruct创建一个新的文件记录
        String filenames = filename.substring(0, filename.lastIndexOf("."));
        FileInfo newFileInfo = fileMapper.toFileInfoForFastUpload(filename,filenames, existingFile, currentUser.getId(), parentId, sha256Hash);

//         如果有任务ID，则进行进度跟踪
        if (taskId != null) {
            // 模拟上传进度（秒传情况下直接完成）
            upLoadProgressService.updateProgress(taskId, fileSize, fileSize);
        }

        // 保存新的文件记录
//        return fileInfoRepository.save(newFileInfo);
        fileInfoMapperService.save(newFileInfo);
//        //保存到ES
//        FileDocument fileDocument = new FileDocument();
//        fileDocument.setId(newFileInfo.getId());
//        fileDocument.setFileName(newFileInfo.getFilename()+ "." + newFileInfo.getFileSuffix());
//        fileDocument.setFileType(newFileInfo.getFileType());
//        fileDocument.setUserId(UserContext.getCurrentUserId());
//        searchRepository.save(fileDocument);
        return newFileInfo;
    }

    /**
     * 文件秒传操作
     * @param fileCheck
     * @return
     */

    @Override
    public Result<FileCheckVO> checkFilesHash(FileCheck fileCheck) {
        String sha256Hash = fileCheck.getSha256Hash();
        List<FileInfo> byHashAndUserId = fileInfoMapperService.findByHashAndUserId(sha256Hash, UserContext.getCurrentUserId());
        if (byHashAndUserId.isEmpty()) {
            //如果没有就不用秒传
            FileCheckVO build = FileCheckVO.builder().exists(false).build();
            return Result.success(build);
        }
        //存在就秒传
        FileCheckVO fvo = FileCheckVO.builder().exists(true).build();
        //todo :文件秒传设置用户已用容量 -> 文件预站 -> 文件失败 -> 释放空间

        //创建新的数据库记录
//        FileInfo fileInfo = byHashAndUserId.get(0);
//        String localFilePath="";
//        String parentId = fileCheck.getParentId();
//        if (parentId!=null){
//            FileInfo byId = fileInfoMapperService.getById(parentId);
//            if(byId!=null) {
//                localFilePath = byId.getLocalFilePath() + fileCheck.getFileName();
//            }
//        }
//        FileInfo fileInfos = fileMapper.toFileInfoFromFileCheck(fileCheck,fileInfo.getPath(),UserContext.getCurrentUserId(), localFilePath,fileInfo.getFileType());
//        fileInfoMapperService.save(fileInfos);
        return Result.success(fvo);
    }


}
