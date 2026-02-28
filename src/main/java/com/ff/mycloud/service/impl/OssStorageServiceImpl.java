package com.ff.mycloud.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSClientBuilder;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.common.util.ExceptionUtil;
import com.ff.mycloud.config.properties.OssProProperties;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    OssProProperties ossProProperties;

    public OssStorageServiceImpl(OssProProperties ossProProperties) {
        this.ossProProperties = ossProProperties;
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

    private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    private OSS getOssClient() {
        return new OSSClientBuilder().build("https://"+ossProProperties.getEndpoint(), ossProProperties.getAccessKeyId(), ossProProperties.getAccessKeySecret());
    }

    private String generateUniqueFileName() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    private String buildObjectName(String path, String fileName) {
//        return path + fileName;
        return path.endsWith(CommonConstants.File.SLASH) ? path + fileName : path + CommonConstants.File.SLASH + fileName;
    }
}
