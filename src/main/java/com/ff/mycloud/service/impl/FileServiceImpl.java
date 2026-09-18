package com.ff.mycloud.service.impl;

import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.common.util.FileHashUtil;
import com.ff.mycloud.common.util.FileTypeUtil;
import com.ff.mycloud.common.util.IpUtil;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.enums.FileObjectStatus;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.*;
import com.ff.mycloud.model.vo.FileCheckVO;
import com.ff.mycloud.model.vo.FileDownloadVO;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.mq.producer.RabbitMqService;
import com.ff.mycloud.repositorys.FileObjectMapper;
import com.ff.mycloud.repositorys.FileRecentAccess;
import com.ff.mycloud.repositorys.UserMapper;
import com.ff.mycloud.service.*;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * 文件服务实现类
 * <p>提供文件的核心业务逻辑，包括：
 * 上传（普通上传和带进度上传）、秒传（基于SHA-256哈希）、
 * 下载、列表、重命名、删除（单文件和递归文件夹）、文件内容获取等功能。
 * 通过MQ异步同步ES索引和发送操作日志。</p>
 *
 * @author 飞飞
 * @date 2026/2/14
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
    private final FileRecentAccess fileRecentAccess;
    StorageServiceFactory storageServiceFactory;
    FileMapper fileMapper;
    FileInfoMapperService fileInfoMapperService;
    UserCenterMapperService userCenterMapperService;
    RabbitMqService rabbitMqService;
    TransactionTemplate transactionTemplate;

    ApacheTikaDocumentParser tikaParser = new ApacheTikaDocumentParser();

    public FileServiceImpl(StorageServiceFactory storageServiceFactory,
                           UserCenterMapperService userCenterMapperService,
                           FileMapper fileMapper,
                           FileInfoMapperService fileInfoMapperService,
                           UserContext userContext,
                           UserMapperServiceImpl userMapperServiceImpl,
                           UpLoadProgressService upLoadProgressService,
                           RabbitMqService rabbitMqService,
                           FileObjectMapper fileObjectMapper, HttpCodeStatusMapper healthHttpCodeStatusMapper, FileRecentAccess fileRecentAccess,
                           TransactionTemplate transactionTemplate) {
        this.storageServiceFactory = storageServiceFactory;
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
        this.userContext = userContext;
        this.userMapperServiceImpl = userMapperServiceImpl;
        this.upLoadProgressService = upLoadProgressService;
        this.userCenterMapperService = userCenterMapperService;
        this.rabbitMqService = rabbitMqService;
        this.fileObjectMapper = fileObjectMapper;
        this.healthHttpCodeStatusMapper = healthHttpCodeStatusMapper;
        this.fileRecentAccess = fileRecentAccess;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 上传文件
     * <p>执行流程：
     * 1. 计算文件SHA-256哈希值
     * 2. 哈希匹配时执行秒传（复用已有文件记录）
     * 3. 否则正常上传到存储系统并保存记录</p>
     *
     * @param file     上传的文件
     * @param parentId 父目录ID
     * @return 上传后的文件路径
     */
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
                log.info("秒传完成: userId={}, fileId={}, fileSize={}", currentUser.getId(), newFileInfo.getId(), file.getSize());
                return newFileInfo.getPath();
            }

            //不存在正常上传 —— OSS 先上传
            StorageService storageService = storageServiceFactory.getStorageService();
            String uploadPath = getUploadPath(parentId, currentUser);
            String filePath = storageService.UploadFile(file, uploadPath);

            String localFilePath;
            if (parentId != null) {
                FileInfo byId = fileInfoMapperService.getById(parentId);
                if (byId != null) {
                    localFilePath = byId.getLocalFilePath() + file.getOriginalFilename();
                } else {
                    localFilePath = "";
                }
            } else {
                localFilePath = "";
            }

            try {
                // DB 操作在事务中执行，失败自动回滚
                FileInfo fileInfo = transactionTemplate.execute(status -> {
                    FileInfo fi = fileMapper.toFileInfo(file, filePath, currentUser.getId(), parentId, localFilePath);
                    fi.setCategoryId(categoryFiles(fi));
                    fileInfoMapperService.save(fi);
                    return fi;
                });
                if (fileInfo != null) {
                    log.info("普通上传完成: userId={}, fileId={}, fileSize={}", currentUser.getId(), fileInfo.getId(), file.getSize());
                }
                return fileInfo != null ? fileInfo.getPath() : null;
            } catch (Exception e) {
                // DB 保存失败，删掉已上传的 OSS 文件作为补偿
                log.error("DB save failed after OSS upload, deleting orphan file: {}", filePath, e);
                try {
                    storageService.deleteFile(filePath);
                } catch (Exception ignored) {
                    log.warn("Failed to delete orphan OSS file: {}", filePath, ignored);
                }
                throw e;
            }
        }
        return null;
    }

    /**
     * 列出指定目录下的所有文件和文件夹
     * <p>文件夹排在前面，文件补充后缀名后返回。</p>
     *
     * @param parentId 父目录ID，为null时列出根目录
     * @return 文件列表视图对象列表
     */
    @Override
    public List<FileListVO> listFiles(Long parentId) {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> fileInfos = fileInfoMapperService.findByParentIdAndUserId(currentUserId,parentId);

        // 补充文件后缀名
        for (FileInfo fileInfo : fileInfos) {
            if (!fileInfo.getIsFolder()) {
                fileInfo.setFilename(fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
            }
            fileInfo.setFilename(fileInfo.getFilename());
        }

        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    /**
     * 从本地文件路径上传文件（支持进度跟踪、秒传、ES同步和操作日志）
     * <p>执行流程：
     * 1. 校验文件存在性
     * 2. 计算SHA-256哈希值
     * 3. 哈希匹配时执行秒传
     * 4. 否则通过存储服务上传
     * 5. 保存文件信息和对象记录
     * 6. 通过MQ异步同步ES和发送操作日志</p>
     *
     * @param fileType         文件MIME类型
     * @param absolutePath     文件的本地绝对路径
     * @param originalFilename 原始文件名
     * @param size             文件大小（字节）
     * @param parentId         父目录ID
     * @param taskId           上传任务ID（用于进度跟踪）
     * @param currentUserId    当前用户ID
     * @throws BusinessException 当文件不存在或不是常规文件时抛出
     */
    @Override
    public void uploadFileWithProgressFromPath(String fileType, String absolutePath, String originalFilename, long size, Long parentId, String taskId, Long currentUserId) throws IOException {
        //根据用户id获得用户信息
        User currentUser = userMapperServiceImpl.findOneByUserId(currentUserId);

        //从路径获取文件
        File file = new File(absolutePath);
        //检查是不是文件并存在
        if (!file.exists()||!file.isFile()){
            log.info("文件不存在或不是常规文件");
            upLoadProgressService.completeUploadTask(taskId, false, "文件不存在或不是常规文件");
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        //计算文件的SHA-256
        String fileHashSHA256 = FileHashUtil.calculateSHA256(file);
        //查看数据库里是否有文件 是否上传过
        //秒传只允许复用"当前用户已经拥有过的文件"。
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
        //不存在文件正常上传 —— OSS 先上传
        String uploadPath = getUploadPath(parentId, currentUser);
        StorageService storageService = storageServiceFactory.getStorageService();
        String path = storageService.uploadFileWithProgressFromPath(file, uploadPath, taskId, originalFilename, size, fileType);

        //新增保存后缀
        String suffix;
        String fileNameWithoutSuffix;
        int i = originalFilename.lastIndexOf(".");
        if (i != -1) {
            suffix = originalFilename.substring(i + 1);
            fileNameWithoutSuffix = originalFilename.substring(0, i);
        } else {
            fileNameWithoutSuffix = "";
            suffix = "";
        }

        // 防重名：同级目录存在同名文件时自动加数字后缀
        String[] resolved = resolveUploadNameConflict(fileNameWithoutSuffix, suffix, parentId, currentUserId);
        final String finalBaseName = resolved[0];
        final String finalSuffix = resolved[1];
        final String resolvedFilename = finalSuffix.isEmpty() ? finalBaseName : finalBaseName + "." + finalSuffix;

        final String localFilePath;
        if (parentId != null) {
            FileInfo byId = fileInfoMapperService.getById(parentId);
            localFilePath = byId != null ? byId.getLocalFilePath() + resolvedFilename : "";
        } else {
            localFilePath = "";
        }

        try {
            // DB 操作在事务中执行，失败自动回滚
            FileInfo fileInfo = transactionTemplate.execute(status -> {
                FileInfo fi = fileMapper.toFileInfoFromPath(finalBaseName, path, size, currentUser.getId(), parentId, fileHashSHA256, finalSuffix, fileType, resolvedFilename, localFilePath);
                fi.setCategoryId(categoryFiles(fi));
                FileObject fo = fileMapper.toFileObject(fi, FileObjectStatus.AVAILABLE);
                fileInfoMapperService.save(fi);
                fileObjectMapper.createFileObject(fo);
                return fi;
            });

            if (fileInfo == null) return;
            Document document = null;
            // 异步同步 PGVector（传原始文本，分片在消费者做）
            if (isDocumentType(fileInfo.getFileSuffix())) {
                document = parseLocalFile(file);
                PgSender pgs = PgSender.builder()
                        .fileId(fileInfo.getId())
                        .contentHash(fileInfo.getSha256Hash())
                        .userId(currentUserId)
                        .text(document.text())
                        .build();
                rabbitMqService.sendInsertPG(pgs);
            }

            // 异步同步 ES
            FileDocument fileDocument = new FileDocument();
            fileDocument.setId(fileInfo.getId());
            if (document != null) {
                fileDocument.setContent(document.text());
            }
            fileDocument.setFileName(fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
            fileDocument.setFileType(fileInfo.getFileType());
            fileDocument.setUserId(currentUserId);
            rabbitMqService.sendInsertMessage(fileDocument);

            UserCenter byUserId = userCenterMapperService.getByUserId(currentUserId);
            long totalSpace = byUserId.getTotalSpace();
            long usedSpace = byUserId.getUsedSpace();

            double usagePercent = usedSpace / (double) totalSpace;
            if (usagePercent > 0.9) {
                UsageMessage usageMessage = new UsageMessage();
                usageMessage.setUserId(currentUserId);
                usageMessage.setUsageSize(usedSpace);
                usageMessage.setTotalSize(totalSpace);
                usageMessage.setMessage("可用空间剩余不足10%，已用"
                        + String.format("%.1f", usagePercent * 100) + "%");
                rabbitMqService.UsageChanged(usageMessage);
            }
            sendOpLog(currentUserId, OperationLog.OP_UPLOAD, fileInfo.getId(),
                    fileDocument.getFileName(), fileInfo.getFileSize(), null);
        } catch (Exception e) {
            // DB 保存失败：删 OSS + 更新 Redis 任务状态
            log.error("DB save failed after OSS upload, cleaning up: path={}, taskId={}", path, taskId, e);
            try {
                storageService.deleteFile(path);
            } catch (Exception ignored) {
                log.warn("Failed to delete orphan OSS file: {}", path, ignored);
            }
            upLoadProgressService.completeUploadTask(taskId, false,
                    "文件信息保存失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            throw e;
        }
    }

    /**
     * 判断是不是可以向量化的文件
     * @param suffix
     * @return
     */
    private boolean isDocumentType(String suffix) {
        return Set.of("pdf", "doc", "docx", "txt", "md", "xlsx", "pptx")
                .contains(suffix != null ? suffix.toLowerCase() : "");
    }

    /**
     * 创建文件样本（预留方法，当前为空实现）
     *
     * @param file       文件对象
     * @param fileObject 文件对象实体
     */
    private void createSample(File file, FileObject fileObject) {

    }

    public Document parseLocalFile(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return tikaParser.parse(is);
        }
    }

    /**
     * 下载文件
     * <p>流程：校验用户权限和文件状态 -> 从存储系统获取文件内容并返回字节数组。</p>
     *
     * @param fileId 文件ID
     * @return 文件的字节数组
     */
    @Override
    public byte[] downloadFile(Long fileId) {
        //先得到fileInfo
        User currentUser = UserContext.getCurrentUser();
        FileInfo andValidateFile = getAndValidateFile(fileId, currentUser);
        // 记录最近访问
        recordAccess(fileId);
        return retrieveFileContent(andValidateFile);
    }

    @Override
    public FileDownloadVO prepareDownload(Long fileId) {
        FileInfo file = getAndValidateFile(fileId, UserContext.getCurrentUser());
        ExceptionUtil.throwIf(Boolean.TRUE.equals(file.getIsFolder()), ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        recordAccess(fileId);
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = file.getFilename();
            if (file.getFileSuffix() != null && !file.getFileSuffix().isBlank()) {
                filename += "." + file.getFileSuffix();
            }
        }
        String contentType = file.getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new FileDownloadVO(file.getPath(), filename, contentType, file.getFileSize());
    }

    /**
     * 获取文件名
     *
     * @param fileId 文件ID
     * @return 文件名
     */
    @Override
    public String getFilename(long fileId) {
        return fileInfoMapperService.getById(fileId).getFilename();
    }

    /**
     * 移动文件/文件夹到新的父目录
     * <p>流程：校验源文件 -> 校验目标目录 -> 防循环嵌套 -> 更新parentId + localFilePath -> 递归更新子节点。</p>
     *
     * @param fileId         文件ID
     * @param targetParentId 目标父目录ID，null或0为根目录
     * @throws BusinessException 当文件不存在、无权限、目标不是文件夹、循环嵌套时抛出
     */
    @Override
    public void moveFile(Long fileId, Long targetParentId) {
        Long currentUserId = UserContext.getCurrentUserId();

        // 1. 校验源文件存在且属于当前用户
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);

        // 2. 解析目标父目录（null/0 = 根目录）
        String newPathPrefix;
        if (targetParentId != null && targetParentId != 0) {
            FileInfo targetParent = fileInfoMapperService.findByIdAndUserId(targetParentId, currentUserId);
            ExceptionUtil.throwIfNull(targetParent, ErrorCode.FILE_NOT_FOUND);
            if (targetParent.getIsFolder() == null || !targetParent.getIsFolder()) {
                throw new BusinessException(ErrorCode.FOLDER_NOT_EMPTY, "目标不是文件夹");
            }
            newPathPrefix = targetParent.getLocalFilePath();
            // 确保以 / 结尾
            if (!newPathPrefix.endsWith("/")) {
                newPathPrefix = newPathPrefix + "/";
            }

            // 3. 防循环嵌套：文件夹不能移动到自己的子孙节点下
            if (file.getIsFolder() != null && file.getIsFolder()) {
                String sourcePath = file.getLocalFilePath();
                String targetPath = targetParent.getLocalFilePath();
                if (targetPath.startsWith(sourcePath)) {
                    throw new BusinessException(ErrorCode.CANNOT_MOVE_INTO_OWN_SUBTREE);
                }
            }
        } else {
            newPathPrefix = "";
            targetParentId = null;
        }

        // 4. 计算新的 localFilePath
        String oldPathPrefix = file.getLocalFilePath();
        String newFilePath;
        if (file.getIsFolder() != null && file.getIsFolder()) {
            newFilePath = newPathPrefix + file.getFilename() + "/";
        } else {
            String fullName = file.getFilename();
            if (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty()) {
                fullName = fullName + "." + file.getFileSuffix();
            }
            newFilePath = newPathPrefix + fullName;
        }

        // 5. 更新根节点
        file.setParentId(targetParentId);
        file.setLocalFilePath(newFilePath);
        file.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);

        // 6. 文件夹：递归更新所有子节点的 localFilePath（前缀替换）
        if (file.getIsFolder() != null && file.getIsFolder()) {
            // getSubTree 查的是 isDeleted=false 的节点
            List<FileInfo> children = fileInfoMapperService.getSubTree(oldPathPrefix, currentUserId);
            for (FileInfo child : children) {
                if (child.getId().equals(fileId)) continue;
                String oldChildPath = child.getLocalFilePath();
                if (oldChildPath != null && oldChildPath.startsWith(oldPathPrefix)) {
                    String relativePart = oldChildPath.substring(oldPathPrefix.length());
                    child.setLocalFilePath(newFilePath + relativePart);
                    child.setUpdatedAt(LocalDateTime.now());
                    fileInfoMapperService.updateById(child);
                }
            }
        }

        log.info("移动成功: fileId={}, oldPath={}, newPath={}", fileId, oldPathPrefix, newFilePath);
    }

    /**
     * 文件复制操作
     * <p>
     * 单文件：直接复制一条 FileInfo + FileObject 记录（共享 OSS 文件）
     * 文件夹：递归复制整棵子树，路径前缀替换，父目录指向新的复制根节点
     * </p>
     *
     * @param fileId 源文件ID
     */
    @Override
    public void copyFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();

        // 1. 校验源文件存在且属于当前用户
        FileInfo source = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(source, ErrorCode.FILE_NOT_FOUND);

        if (source.getIsFolder() != null && source.getIsFolder()) {
            // ========== 文件夹：递归复制 ==========
            String oldPrefix = source.getLocalFilePath();
            // 去掉末尾 /
            String oldPrefixNormalized = oldPrefix.endsWith("/") ? oldPrefix.substring(0, oldPrefix.length() - 1) : oldPrefix;

            List<FileInfo> children = fileInfoMapperService.getSubTree(oldPrefixNormalized, currentUserId);

            // 先复制根文件夹，拿到新 ID（防重名）
            String copyName = resolveCopyName(source.getFilename(), null, source.getParentId(), currentUserId, true);
            FileInfo newRoot = cloneFileInfo(source);
            newRoot.setFilename(copyName);
            String newRootPath = source.getLocalFilePath().replaceFirst(
                    Pattern.quote(source.getFilename()) + "/?$",
                    newRoot.getFilename() + "/");
            newRoot.setLocalFilePath(newRootPath);
            fileInfoMapperService.save(newRoot);
            Long newRootId = newRoot.getId();

            // 处理子节点
            for (FileInfo child : children) {
                if (child.getId().equals(source.getId())) {
                    continue; // 跳过根文件夹，已经处理
                }

                FileInfo newChild = cloneFileInfo(child);
                // 路径前缀替换
                if (child.getLocalFilePath() != null && oldPrefixNormalized != null) {
                    String relativePart = child.getLocalFilePath().substring(oldPrefixNormalized.length());
                    newChild.setLocalFilePath(newRootPath + relativePart);
                }
                // 父目录指向新根
                if (child.getParentId().equals(source.getId())) {
                    newChild.setParentId(newRootId);
                }
                fileInfoMapperService.save(newChild);
            }

            log.info("文件夹复制完成：fileId={}, 共{}个节点", fileId, children.size());
        } else {
            // ========== 单文件：直接复制 ==========
            String copyName = resolveCopyName(source.getFilename(), source.getFileSuffix(), source.getParentId(), currentUserId, false);
            FileInfo copyFile = cloneFileInfo(source);
            copyFile.setFilename(copyName);
            fileInfoMapperService.save(copyFile);

            // 秒传：共享同一个 OSS 文件，创建 FileObject 引用
            FileObject fileObject = fileMapper.toFileObject(copyFile, FileObjectStatus.AVAILABLE);
            fileObjectMapper.createFileObject(fileObject);

            log.info("文件复制成功：fileId={}", fileId);
        }
    }

    /**
     * 克隆 FileInfo 基础字段（不含 id/time，由 DB 自动生成）
     */
    private FileInfo cloneFileInfo(FileInfo source) {
        FileInfo f = new FileInfo();
        f.setFilename(source.getFilename());
        f.setOriginalFilename(source.getOriginalFilename());
        f.setPath(source.getPath());
        f.setFileSize(source.getFileSize());
        f.setFileType(source.getFileType());
        f.setSha256Hash(source.getSha256Hash());
        f.setFileSuffix(source.getFileSuffix());
        f.setUserId(source.getUserId());
        f.setParentId(source.getParentId());
        f.setIsFolder(source.getIsFolder());
        f.setIsDeleted(false);
        f.setLocalFilePath(source.getLocalFilePath());
        f.setCategoryId(source.getCategoryId());
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        return f;
    }

    /**
     * 重命名文件
     * <p>流程：校验新文件名 -> 查询文件存在 -> 去除文件名中的后缀 -> 更新数据库 -> MQ异步同步ES重命名 + 操作日志。</p>
     *
     * @param fileId      文件ID
     * @param newFilename 新文件名
     * @throws BusinessException 当文件名为空或文件不存在时抛出
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void renameFile(Long fileId, String newFilename) {

        if (newFilename == null || newFilename.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILENAME);
        }

        FileInfo fileInfo = fileInfoMapperService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!java.util.Objects.equals(fileInfo.getUserId(), UserContext.getCurrentUserId()) || Boolean.TRUE.equals(fileInfo.getIsDeleted())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        newFilename = newFilename.trim();
        String suffix = fileInfo.getFileSuffix();
        boolean folder = Boolean.TRUE.equals(fileInfo.getIsFolder());
        if (!folder && suffix != null && newFilename.toLowerCase(java.util.Locale.ROOT).endsWith("." + suffix.toLowerCase(java.util.Locale.ROOT))) {
            newFilename = newFilename.substring(0, newFilename.length()-suffix.length()-1);
        }
        if (newFilename.isBlank() || newFilename.length()>50 || newFilename.equals(".") || newFilename.equals("..") ||
                newFilename.chars().anyMatch(c -> c<32 || "\\/:*?\"<>|".indexOf(c)>=0)) throw new BusinessException(ErrorCode.INVALID_FILENAME);
        String oldName = fileInfo.getFilename();
        for (FileInfo sibling : fileInfoMapperService.findByParentIdAndUserId(fileInfo.getUserId(),fileInfo.getParentId()==null?0L:fileInfo.getParentId())) {
            if (!fileId.equals(sibling.getId()) && newFilename.equalsIgnoreCase(sibling.getFilename()) &&
                    java.util.Objects.equals(fileInfo.getIsFolder(),sibling.getIsFolder()) && java.util.Objects.equals(suffix,sibling.getFileSuffix()))
                throw new BusinessException(ErrorCode.INVALID_FILENAME,"目标目录已存在同名文件");
        }
        String oldPath = fileInfo.getLocalFilePath();
        String newPath = oldPath;
        if (oldPath != null) {
            String parentPath = folder && oldPath.endsWith(oldName+"/") ? oldPath.substring(0,oldPath.length()-oldName.length()-1) : oldPath.substring(0,oldPath.lastIndexOf('/')+1);
            newPath = parentPath + newFilename + (folder?"/":suffix==null||suffix.isBlank()?"":"."+suffix);
        }
        List<FileInfo> descendants = folder && oldPath!=null ? fileInfoMapperService.getSubTree(oldPath,fileInfo.getUserId()):List.of();
        fileInfo.setFilename(newFilename);
        fileInfo.setLocalFilePath(newPath);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapperService.updateById(fileInfo);
        for (FileInfo child : descendants) {
            if (!fileId.equals(child.getId()) && child.getLocalFilePath()!=null && child.getLocalFilePath().startsWith(oldPath)) {
                child.setLocalFilePath(newPath+child.getLocalFilePath().substring(oldPath.length()));
                child.setUpdatedAt(LocalDateTime.now()); fileInfoMapperService.updateById(child);
            }
        }
//
        //异步同步ES重命名
        FileDocument fd = new FileDocument();
        fd.setId(fileId);
        fd.setFileName(fileInfo.getIsFolder() ? newFilename : newFilename + "." + fileInfo.getFileSuffix());
        rabbitMqService.sendUpdateMessage(fd);
        sendOpLog(OperationLog.OP_RENAME, fileId,
                fd.getFileName(), fileInfo.getFileSize(),
                "原文件名:" + oldName);
        log.info("文件重命名完成: fileId={}, userId={}", fileId, fileInfo.getUserId());
    }

    /**
     * 简单删除单个文件（不处理文件夹递归）
     * <p>流程：逻辑删除记录 -> 更新用户已用空间 -> 判断哈希引用数 -> 无引用时物理删除存储文件。</p>
     *
     * @param fileId 文件ID
     */
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

    /**
     * 删除文件或文件夹
     * <p>核心分支判断：
     * <ul>
     *   <li>文件夹：获取子树后递归处理（逻辑删除所有子节点 + 物理删除无引用的文件）</li>
     *   <li>单文件：直接处理当前文件</li>
     * </ul>
     * 同时通过MQ异步同步ES删除和发送操作日志，最后更新用户已用空间。</p>
     *
     * @param fileId 文件ID
     * @throws IllegalArgumentException 当fileId为空时抛出
     * @throws RuntimeException 当文件不存在、无权限或用户不存在时抛出
     */
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
            //异步同步ES删除 + 操作日志
            FileDocument fd = new FileDocument();
            fd.setId(file.getId());
            rabbitMqService.sendDeleteMessage(fd);
            if (!file.getIsFolder()) {
                String name = file.getFilename();
                if (file.getFileSuffix() != null && !file.getFileSuffix().isEmpty()) {
                    name = name + "." + file.getFileSuffix();
                }
                sendOpLog(OperationLog.OP_DELETE, file.getId(), name, file.getFileSize(), null);
            }

            // 物理删除（仅文件需要，且仅当无引用时）
            if (!file.getIsFolder()) {
                long referenceCount = fileInfoMapperService.countFiles(file.getSha256Hash());
                log.debug("文件{}引用数：{}", file.getLocalFilePath(), referenceCount);

                // 无其他引用时物理删除存储文件
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

    /**
     * 获取文件内容（仅根据ID，不校验用户权限）
     *
     * @param id 文件ID
     * @return 文件的字节数组
     * @throws BusinessException 当文件不存在时抛出
     */
    @Override
    public byte[] getFileContent(Long id) {
        FileInfo byId = fileInfoMapperService.getById(id);
        if (byId==null){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return retrieveFileContent(byId);
    }

    /**
     * 根据存储路径获取文件输入流
     *
     * @param path 文件在存储系统中的路径
     * @return 文件的输入流
     */
    @Override
    public InputStream getFile(String path) {
        StorageService storageService = storageServiceFactory.getStorageService();
        return storageService.getFile(path);
    }


    /**
     * 从存储系统获取文件内容
     * <p>校验是否为文件夹（不能下载文件夹），然后通过存储服务下载文件字节数组。</p>
     *
     * @param andValidateFile 已验证的文件信息
     * @return 文件的字节数组
     */
    private byte[] retrieveFileContent(FileInfo andValidateFile) {
        ExceptionUtil.throwIf(andValidateFile.getIsFolder(),ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        String filePath = andValidateFile.getPath();
        StorageService storageService = storageServiceFactory.getStorageService();
        return storageService.downloadFile(filePath);
    }

    /**
     * 获取并校验文件权限
     * <p>校验：文件存在、属于当前用户、未被删除。</p>
     *
     * @param fileId       文件ID
     * @param currentUser  当前用户
     * @return 校验通过的文件信息
     * @throws BusinessException 当文件不存在、无权限或已删除时抛出
     */
    private FileInfo getAndValidateFile(Long fileId, User currentUser) {
        FileInfo byId = fileInfoMapperService.getById(fileId);
        if (byId==null){
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ExceptionUtil.throwIf(!byId.getUserId().equals(currentUser.getId()),ErrorCode.NO_PERMISSION);
        ExceptionUtil.throwIf(byId.getIsDeleted(),ErrorCode.FILE_NOT_FOUND);
        return byId;
    }


    /**
     * 获取上传目标路径
     * <p>根据用户ID生成存储路径前缀。</p>
     *
     * @param parentId     父目录ID（当前未使用，预留扩展）
     * @param currentUser  当前用户
     * @return 上传路径字符串
     */
    private String getUploadPath(Long parentId, User currentUser) {
        //1.如果没有传入父路径的情况
        String path = CommonConstants.File.FILE_PATH_PREFIX + currentUser.getId();
        return path;
    }

    /**
     * 处理秒传操作
     * <p>原理：不实际上传文件，在数据库生成新记录复用已有文件的存储路径。
     * 通过MQ异步同步ES索引和发送操作日志。</p>
     *
     * @param filename     文件名
     * @param fileSize     文件大小
     * @param existingFile 已存在的文件记录
     * @param sha256Hash   文件SHA-256哈希值
     * @param parentId     父目录ID
     * @param taskId       上传任务ID（有则更新进度）
     * @param currentUser  当前用户
     * @return 新生成的文件信息
     */
//    这个原理就是不用上传文件 在数据库生成一个记录就行 不管用户文甲明是不是一样 只要路径对应就行
    private FileInfo handleFastUpload(String filename, long fileSize, FileInfo existingFile, String sha256Hash, Long parentId, String taskId, User currentUser) {
        // 防重名：提取后缀并检查同名冲突
        int dotIdx = filename.lastIndexOf(".");
        String baseName = dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
        String suffix = dotIdx > 0 ? filename.substring(dotIdx + 1) : "";
        String[] resolved = resolveUploadNameConflict(baseName, suffix, parentId, currentUser.getId());
        String finalBaseName = resolved[0];
        String finalSuffix = resolved[1];
        String resolvedFilename = finalSuffix.isEmpty() ? finalBaseName : finalBaseName + "." + finalSuffix;

        // 使用MapStruct创建一个新的文件记录
        FileInfo newFileInfo = fileMapper.toFileInfoForFastUpload(resolvedFilename, finalBaseName, existingFile, currentUser.getId(), parentId, sha256Hash);
        newFileInfo.setCategoryId(categoryFiles(newFileInfo));

//         如果有任务ID，则进行进度跟踪
        if (taskId != null) {
            // 模拟上传进度（秒传情况下直接完成）
            upLoadProgressService.updateProgress(taskId, fileSize, fileSize);
        }

        // 保存新的文件记录
//        return fileInfoRepository.save(newFileInfo);
        fileInfoMapperService.save(newFileInfo);
        //异步同步ES
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(newFileInfo.getId());
        fileDocument.setFileName(newFileInfo.getFilename() + "." + newFileInfo.getFileSuffix());
        fileDocument.setFileType(newFileInfo.getFileType());
        fileDocument.setUserId(currentUser.getId());
        rabbitMqService.sendInsertMessage(fileDocument);
        sendOpLog(currentUser.getId(), OperationLog.OP_UPLOAD, newFileInfo.getId(),
                resolvedFilename, newFileInfo.getFileSize(), "秒传");
        return newFileInfo;
    }

    /**
     * 文件秒传校验
     * <p>根据SHA-256哈希值查询文件是否已存在，返回是否可秒传的结果。</p>
     *
     * @param fileCheck 文件校验参数
     * @return 文件校验结果视图（exists=true表示可秒传）
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
        //增加size判断 避免假秒传（hash碰撞或前端篡改fileSize）
        FileInfo existingFile = byHashAndUserId.get(0);
        if (!fileCheck.getFileSize().equals(existingFile.getFileSize())) {
            //不是秒传
            FileCheckVO build = FileCheckVO.builder().exists(false).build();
            return Result.success(build);
        }
        //存在就秒传
        FileCheckVO fvo = FileCheckVO.builder().exists(true).build();

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

    /**
     * 收藏文件
     * <p>校验文件存在且属于当前用户、未被删除，然后设置收藏标记。</p>
     *
     * @param fileId 文件ID
     */
    @Override
    public void starFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);

        file.setIsStarred(true);
        file.setStarredAt(LocalDateTime.now());
        fileInfoMapperService.updateById(file);
        log.info("收藏文件成功：fileId={}", fileId);
    }

    /**
     * 取消收藏文件
     * <p>校验文件存在且属于当前用户，然后清除收藏标记。</p>
     *
     * @param fileId 文件ID
     */
    @Override
    public void unstarFile(Long fileId) {
        Long currentUserId = UserContext.getCurrentUserId();
        FileInfo file = fileInfoMapperService.findByIdAndUserId(fileId, currentUserId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);

        file.setIsStarred(false);
        file.setStarredAt(null);
        fileInfoMapperService.updateById(file);
        log.info("取消收藏文件：fileId={}", fileId);
    }

    /**
     * 获取当前用户的收藏文件列表
     * <p>查询 isStarred=true 且未被删除的文件，补充后缀名后返回。</p>
     *
     * @return 收藏的文件列表
     */
    @Override
    public List<FileListVO> listStarredFiles() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> starredFiles = fileInfoMapperService.findStarredFiles(currentUserId);

        for (FileInfo fileInfo : starredFiles) {
            if (!fileInfo.getIsFolder()) {
                fileInfo.setFilename(fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
            }
            fileInfo.setFilename(fileInfo.getFilename());
        }

        return starredFiles.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public void recordAccess(Long fileId) {
        fileRecentAccess.recordAccess(UserContext.getCurrentUserId(), fileId, LocalDateTime.now());
    }

    @Override
    public int categoryFiles(FileInfo fileInfo) {
        return FileTypeUtil.getCategoryId(fileInfo.getFileSuffix());
    }

    @Override
    public List<FileListVO> getRecentFiles() {
        Long currentUserId = UserContext.getCurrentUserId();
        List<FileInfo> recentFiles = fileRecentAccess.getRecentFiles(currentUserId, CommonConstants.RecentFile.N);

        for (FileInfo fileInfo : recentFiles) {
            if (!fileInfo.getIsFolder()) {
                fileInfo.setFilename(fileInfo.getFilename() + "." + fileInfo.getFileSuffix());
            }
            fileInfo.setFilename(fileInfo.getFilename());
        }

        return recentFiles.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    /**
     * 异步发送操作日志（通过MQ）
     * <p>组装操作日志实体并通过RabbitMQ异步发送，包含用户ID、操作类型、文件信息、客户端IP等。</p>
     *
     * @param operation 操作类型（如上传、删除、重命名）
     * @param fileId    文件ID
     * @param filename  文件名
     * @param fileSize  文件大小
     * @param extraInfo 额外描述信息（如"秒传"、原文件名等）
     */
    private void sendOpLog(String operation, Long fileId, String filename, Long fileSize, String extraInfo) {
        sendOpLog(UserContext.getCurrentUserId(), operation, fileId, filename, fileSize, extraInfo);
    }

    private void sendOpLog(Long userId, String operation, Long fileId, String filename, Long fileSize, String extraInfo) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperation(operation);
        log.setFileId(fileId);
        log.setFilename(filename);
        log.setFileSize(fileSize);
        log.setExtraInfo(extraInfo);
        log.setClientIp(IpUtil.getClientIp());
        log.setCreateTime(LocalDateTime.now());
        rabbitMqService.sendLogMessage(log);
    }

    /**
     * 上传文件同名冲突解决：同级目录存在同名文件时自动加数字后缀。
     * <p>规则：test.txt → test(1).txt → test(2).txt → ...</p>
     *
     * @param baseName      文件名（不含后缀）
     * @param suffix        文件后缀（不含点）
     * @param parentId      父目录ID
     * @param currentUserId 当前用户ID
     * @return [resolvedBaseName, resolvedSuffix]
     */
    private String[] resolveUploadNameConflict(String baseName, String suffix, Long parentId, Long currentUserId) {
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        String resolvedBase = baseName;
        String resolvedSuffix = suffix;

        // 检查是否有同名冲突
        if (hasNameConflict(resolvedBase, resolvedSuffix, siblings, null)) {
            int counter = 1;
            while (hasNameConflict(resolvedBase + "(" + counter + ")", resolvedSuffix, siblings, null) && counter < 1000) {
                counter++;
            }
            resolvedBase = baseName + "(" + counter + ")";
            log.info("上传文件同名冲突，自动重命名：{}.{} -> {}.{}", baseName, suffix, resolvedBase, resolvedSuffix);
        }

        return new String[]{resolvedBase, resolvedSuffix};
    }

    /**
     * 复制文件同名冲突解决：同级目录存在"xxx(复制)"时自动递增。
     */
    private String resolveCopyName(String baseName, String suffix, Long parentId, Long currentUserId, boolean isFolder) {
        List<FileInfo> siblings = fileInfoMapperService.findByParentIdAndUserId(currentUserId, parentId);
        String candidate = baseName + "(复制)";
        if (!hasCopyNameConflict(candidate, suffix, siblings, null, isFolder)) {
            return candidate;
        }
        for (int i = 2; i <= 99; i++) {
            candidate = baseName + "(复制" + i + ")";
            if (!hasCopyNameConflict(candidate, suffix, siblings, null, isFolder)) {
                return candidate;
            }
        }
        return baseName + "(复制" + System.currentTimeMillis() % 100000 + ")";
    }

    private boolean hasCopyNameConflict(String name, String suffix, List<FileInfo> siblings, Long excludeId, boolean isFolder) {
        if (siblings == null) return false;
        for (FileInfo f : siblings) {
            if (excludeId != null && f.getId().equals(excludeId)) continue;
            if (!Objects.equals(f.getIsFolder(), isFolder)) continue;
            if (name.equalsIgnoreCase(f.getFilename())) return true;
        }
        return false;
    }

    private boolean hasNameConflict(String name, String suffix, List<FileInfo> siblings, Long excludeId) {
        if (siblings == null) return false;
        String fullName = suffix != null && !suffix.isEmpty() ? name + "." + suffix : name;
        for (FileInfo f : siblings) {
            if (excludeId != null && f.getId().equals(excludeId)) continue;
            String fName = f.getFileSuffix() != null && !f.getFileSuffix().isEmpty()
                    ? f.getFilename() + "." + f.getFileSuffix()
                    : f.getFilename();
            if (fullName.equalsIgnoreCase(fName)) return true;
        }
        return false;
    }

}
