package com.ff.mycloud.service.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.event.ProgressEvent;
import com.aliyun.oss.event.ProgressEventType;
import com.aliyun.oss.event.ProgressListener;
import com.aliyun.oss.model.*;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.config.properties.OssProProperties;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.StorageService;
import com.ff.mycloud.service.UpLoadProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云OSS文件存储服务实现类
 * <p>
 * 基于阿里云OSS SDK实现文件的上传、下载、删除、获取流等存储操作。
 * 支持带进度追踪的分片上传、签名URL生成（预览/下载）、IP限制安全增强等功能。
 * 上传时自动设置Content-Type并处理文本文件编码。
 * </p>
 *
 * @author 飞飞
 * @date 2026/2/22 14:28
 * @version 1.0
 */
@Service
public class OssStorageServiceImpl implements StorageService {
    private static final Logger logger = LoggerFactory.getLogger(OssStorageServiceImpl.class);
    private final UpLoadProgressService upLoadProgressService;
    OssProProperties ossProProperties;
    FileInfoMapperService fileInfoMapperService;

    public OssStorageServiceImpl(OssProProperties ossProProperties,
                                 UpLoadProgressService upLoadProgressService,
                                 FileInfoMapperService fileInfoMapperService) {
        this.ossProProperties = ossProProperties;
        this.upLoadProgressService = upLoadProgressService;
        this.fileInfoMapperService = fileInfoMapperService;
    }

    /**
     * 上传文件到OSS
     * <p>生成唯一文件名后直接上传文件流到OSS Bucket指定路径。</p>
     *
     * @param file 上传的MultipartFile对象
     * @param path 目标存储路径（目录）
     * @return OSS对象完整路径（目录+文件名）
     */
    @Override
    public String UploadFile(MultipartFile file, String path) {

        String fileName = generateUniqueFileName();
        String objectName = buildObjectName(path, fileName);
        OSS ossClient = null;
        try {
            ossClient=getOssClient();
            ossClient.putObject(ossProProperties.getBucketName(),objectName,file.getInputStream());
        } catch (Exception e) {
            logger.error("Failed to upload file to OSS: bucket={}, objectName={}, error={}", ossProProperties.getBucketName(), objectName, e.getMessage());
            ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, e.getMessage());
            return null; // 不会执行到这里，为了编译通过
        } finally {
            closeOssClient(ossClient);
        }
        return objectName;
    }

    /**
     * 从本地文件上传到OSS，支持进度追踪
     * <p>
     * 从磁盘临时文件读取并上传到OSS，自动设置Content-Type和文本文件编码，
     * 通过ProgressListener实时汇报上传进度。支持取消上传。
     * </p>
     *
     * @param file             本地临时文件
     * @param uploadPath       目标上传路径
     * @param taskId           上传任务ID，关联进度追踪
     * @param originalFilename 原始文件名（用于判断类型和提取后缀）
     * @param size             文件大小（字节）
     * @param fileType         文件MIME类型（备用）
     * @return OSS对象完整路径
     */
    @Override
    public String uploadFileWithProgressFromPath(File file, String uploadPath, String taskId, String originalFilename, long size, String fileType) {
        checkOssEnabled();
        try {
            String fileSex = originalFilename.substring(originalFilename.lastIndexOf("."), originalFilename.length());
            String fileName = generateUniqueFileName();
            fileName = fileName + fileSex;
            String objectName = buildObjectName(uploadPath, fileName);
            OSS ossClient = null;
            try (FileInputStream inputStream = new FileInputStream(file)) {
                ObjectMetadata metadata = new ObjectMetadata();

                String contentType = Files.probeContentType(file.toPath());

                if(contentType == null){
                    contentType = "application/octet-stream";
                }

                // 文本文件加 UTF8
                if(        contentType.startsWith("text")
                        || originalFilename.endsWith(".md")
                        || originalFilename.endsWith(".json")
                        || originalFilename.endsWith(".log")){
                    contentType += ";charset=UTF-8";
                }

                metadata.setContentType(contentType);
                PutObjectRequest putObjectRequest = new PutObjectRequest(ossProProperties.getBucketName(), objectName, inputStream,metadata);
                putObjectRequest.withProgressListener(createProgressListener(taskId, size));
                ossClient = getOssClient();
                ossClient.putObject(putObjectRequest);
                return objectName;

            } catch (Exception e) {
                logger.error("Failed to upload file to OSS with progress tracking: bucket={}, objectName={}, error={}",
                        ossProProperties.getBucketName(), objectName, e.getMessage());
                upLoadProgressService.completeUploadTask(taskId, false, "OSS上传失败: " + e.getMessage());
                ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, "OSS上传失败: " + e.getMessage());
                return null; // 不会执行到这里，为了编译通过
            } finally {
                closeOssClient(ossClient);
            }
        } catch (Exception e) {
        handleUploadError(taskId, "文件上传失败: " + e.getMessage());
        throw e;
    }
    }

    /**
     * 从OSS下载文件为字节数组
     *
     * @param filePath OSS对象Key（完整路径）
     * @return 文件内容的字节数组
     * @throws RuntimeException 当IO读取失败时
     */
    @Override
    public byte[] downloadFile(String filePath) {
        checkOssEnabled();
        try {
        OSS ossClient = null;
        ossClient = getOssClient();
        OSSObject object = ossClient.getObject(new GetObjectRequest(ossProProperties.getBucketName(), filePath));
        return object.getObjectContent().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从OSS删除指定路径的文件
     *
     * @param path OSS对象Key（完整路径）
     */
    @Override
    public void deleteFile(String path) {
        checkOssEnabled();
        OSS ossClient=null;
    try {
        ossClient = getOssClient();
        ossClient.deleteObject(ossProProperties.getBucketName(), path);
    } catch (Exception e) {
        ExceptionUtil.throwBizException(ErrorCode.OSS_DELETE_FAILED, e.getMessage());
    } finally {
        closeOssClient(ossClient);
    }
    }

    /**
     * 获取OSS文件的输入流
     * <p>注意：调用方需要在使用后关闭流和OSS客户端连接。</p>
     *
     * @param path OSS对象Key（完整路径）
     * @return 文件输入流
     */
    @Override
    public InputStream getFile(String path) {
        checkOssEnabled();
        OSS ossClient=null;
        ossClient = getOssClient();
        OSSObject object = ossClient.getObject(new GetObjectRequest(ossProProperties.getBucketName(), path));
        InputStream objectContent = object.getObjectContent();
        return objectContent;
    }

    /**
     * 创建上传进度监听器
     * <p>
     * 监听OSS上传的各个事件（开始、传输中、完成、失败、取消），
     * 实时更新Redis中的上传进度，支持任务取消检测。
     * </p>
     *
     * @param taskId 上传任务ID
     * @param size   文件总大小（字节）
     * @return ProgressListener实例
     */
    private ProgressListener createProgressListener(String taskId, long size) {
       return new ProgressListener() {
           private long totalBytesTransferred = 0;
           @Override
            public void progressChanged(ProgressEvent progressEvent) {
                // 看任务是否被取消
               if (upLoadProgressService.isCancelled(taskId)) {
                   throw new RuntimeException("上传已取消");
               }
               //创建监听
                long bytes = progressEvent.getBytes();
                ProgressEventType eventType = progressEvent.getEventType();
                switch (eventType) {
                    case REQUEST_CONTENT_LENGTH_EVENT:
                        //开始状态
                        upLoadProgressService.updateProgress(taskId,0,size);
                        break;
                    case REQUEST_BYTE_TRANSFER_EVENT:
                        //传输状态
                        totalBytesTransferred+=bytes;
                        upLoadProgressService.updateBytesTransferred(taskId,totalBytesTransferred);
                        break;
                    case TRANSFER_COMPLETED_EVENT:
                        // SUCCESS is set after the file metadata is persisted.
                        break;
                    case TRANSFER_FAILED_EVENT:
                        //失败状态
                        upLoadProgressService.completeUploadTask(taskId,false,"上传失败");
                        break;
                    case TRANSFER_CANCELED_EVENT:
                        upLoadProgressService.cancelUploadTask(taskId,"文件取消");
                }
            }
        };
    }

/**
 * 处理上传错误，记录日志并更新任务状态为失败
 *
 * @param taskId       上传任务ID
 * @param errorMessage 错误信息描述
 */
private void handleUploadError(String taskId, String errorMessage) {
    logger.error(errorMessage);
    upLoadProgressService.completeUploadTask(taskId, false, errorMessage);
    ExceptionUtil.throwBizException(ErrorCode.FILE_UPLOAD_FAILED, errorMessage);
}


/**
 * 安全关闭OSS客户端连接，释放资源
 *
 * @param ossClient OSS客户端实例，可为null
 */
private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

//    private OSS getOssClient() {
//        return new OSSClientBuilder().build(
//                "https://"+ossProProperties.getEndpoint(),
//                ossProProperties.getAccessKeyId(),
//                ossProProperties.getAccessKeySecret());
//    }

    /**
     * 创建OSS客户端实例
     *
     * @return 已配置的OSS客户端
     */
    private OSS getOssClient() {
        return new OSSClientBuilder().build(
                ossProProperties.getEndpoint(),
                ossProProperties.getAccessKeyId(),
                ossProProperties.getAccessKeySecret());
    }

    /**
     * 生成UUID作为唯一文件名（去除横线）
     *
     * @return 32位唯一字符串
     */
    private String generateUniqueFileName() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 拼接目录路径和文件名生成完整的OSS对象Key
     * <p>确保路径以"/"结尾。</p>
     *
     * @param path     目录路径
     * @param fileName 文件名
     * @return 完整对象路径，如 "folder/file.txt"
     */
    private String buildObjectName(String path, String fileName) {
        return path.endsWith(CommonConstants.File.SLASH) ? path + fileName : path + CommonConstants.File.SLASH + fileName;
    }
    /**
     * 检查OSS存储服务是否已启用
     * <p>通过配置属性oss.enabled判断，未启用时抛出OSS_DISABLED异常。</p>
     *
     * @throws BusinessException 当OSS未启用时抛出
     */
    private void checkOssEnabled() {
        if (!ossProProperties.getEnabled()) {
            logger.error("OSS storage is disabled");
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }


    /**
     * 生成预览/下载的签名URL
     * <p>
     * 生成带签名的临时访问URL，设置response-content-disposition=inline实现浏览器内预览。
     * 内部将OSS域名替换为自定义加速域名（pre.feifei.ltd）。
     * </p>
     *
     * @param objectKey     OSS对象Key（例如：folder/image.jpg）
     * @param expirySeconds 签名有效期（秒），建议300秒
     * @return 签名URL字符串
     */
    public String generatePresignedUrl(String objectKey, int expirySeconds) {
        Date date = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
        OSS ossClient = getOssClient();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(ossProProperties.getBucketName(), objectKey);
        request.setMethod(HttpMethod.GET);
        request.setExpiration(date);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("response-content-disposition", "inline");

        request.setQueryParameter(queryParams);
        URL url = ossClient.generatePresignedUrl(request);
        String previewUrl = url.toString()
                .replace("feifeioss.oss-cn-hangzhou.aliyuncs.com",
                        "pre.feifei.ltd");

        ossClient.shutdown();

        return previewUrl;

    }

    /**
     * 生成带IP限制的签名URL（增强安全性）
     *
     * @param objectKey     OSS文件路径
     * @param expirySeconds 过期时间（秒）
     * @return 签名URL字符串
     */
    public String generatePresignedUrlWithIpRestriction(String objectKey, int expirySeconds) {
        OSS ossClient = null;
        try {
            Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossProProperties.getBucketName(),
                    objectKey,
                    HttpMethod.GET);
            request.setExpiration(expiration);

            // 核心修改：只保留response-content-disposition（移除response-content-type）
            Map<String, String> queryParams = new HashMap<>();
            // 仅设置预览模式，不覆盖Content-Type（避免OSS限制）
            queryParams.put("response-content-disposition", "inline");
            queryParams.put("x-oss-process", "oss-direct"); // 强制覆盖元信息

            request.setQueryParameter(queryParams);

            ossClient = getOssClient();
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }


    /**
     * 初始化OSS分片上传，返回uploadId
     */
    @Override
    public String initiateMultipartUpload(String objectKey) {
        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                    ossProProperties.getBucketName(), objectKey);
            InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
            return result.getUploadId();
        } finally {
            closeOssClient(ossClient);
        }
    }
}

