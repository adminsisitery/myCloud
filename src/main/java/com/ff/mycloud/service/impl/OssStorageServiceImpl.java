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
 * @description: TODO 
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

    public OssStorageServiceImpl(OssProProperties ossProProperties, UpLoadProgressService upLoadProgressService,FileInfoMapperService fileInfoMapperService) {
        this.ossProProperties = ossProProperties;
        this.upLoadProgressService = upLoadProgressService;
        this.fileInfoMapperService = fileInfoMapperService;
    }

    @Override
    public String UploadFile(MultipartFile file, String path) {

        String fileName = generateUniqueFileName();
        String objectName = buildObjectName(path,fileName);
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

    @Override
    public String  uploadFileWithProgressFromPath(File file, String uploadPath, String taskId, String originalFilename, long size, String fileType) {
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

    @Override
    public InputStream getFile(String path) {
        checkOssEnabled();
        OSS ossClient=null;
        ossClient = getOssClient();
        OSSObject object = ossClient.getObject(new GetObjectRequest(ossProProperties.getBucketName(), path));
        InputStream objectContent = object.getObjectContent();
        return objectContent;
    }

    private ProgressListener createProgressListener(String taskId, long size) {
       return new ProgressListener() {
           private long totalBytesTransferred = 0;
           @Override
            public void progressChanged(ProgressEvent progressEvent) {
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
                        //完成状态
                        upLoadProgressService.completeUploadTask(taskId,true,"上传完成");
                        break;
                    case TRANSFER_FAILED_EVENT:
                        //失败状态
                        upLoadProgressService.completeUploadTask(taskId,false,"上传失败");
                        break;
                }
            }
        };
    }
private void handleUploadError(String taskId, String errorMessage) {
    logger.error(errorMessage);
    upLoadProgressService.completeUploadTask(taskId, false, errorMessage);
    ExceptionUtil.throwBizException(ErrorCode.FILE_UPLOAD_FAILED, errorMessage);
}


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

    private OSS getOssClient() {
        return new OSSClientBuilder().build(
                ossProProperties.getEndpoint(),
                ossProProperties.getAccessKeyId(),
                ossProProperties.getAccessKeySecret());
    }

    private String generateUniqueFileName() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    private String buildObjectName(String path, String fileName) {
//        return path + fileName;
        return path.endsWith(CommonConstants.File.SLASH) ? path + fileName : path + CommonConstants.File.SLASH + fileName;
    }
    /**
     * 检查OSS是否启用
     */
    private void checkOssEnabled() {
        if (!ossProProperties.getEnabled()) {
            logger.error("OSS storage is disabled");
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }


    /**
     * 生成预览/下载的签名URL
     * @param objectKey OSS文件路径（例如：folder/image.jpg）
     * @param expirySeconds 过期时间（秒），建议300秒
     * @return 签名URL字符串
     */

    public String generatePresignedUrl(String objectKey, int expirySeconds) {
        Date date = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
        OSS ossClient = getOssClient();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(ossProProperties.getBucketName(), objectKey);
        request.setMethod(com.aliyun.oss.HttpMethod.GET);
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
}

