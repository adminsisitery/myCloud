package com.cloudrive.aiService.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 存储服务抽象
 * <p>ai-service 的 OSS 读写最小子集：SAVE_DOCUMENT 上传、Agent 全文读取、上传失败补偿删除。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface StorageService {

    /**
     * 上传文件到存储，返回对象路径（objectName）
     *
     * @param file 文件
     * @param path 存储目录前缀
     * @return 对象路径
     */
    String UploadFile(MultipartFile file, String path);

    /**
     * 读取存储对象
     *
     * @param path 对象路径
     * @return 输入流（调用方负责关闭）
     */
    InputStream getFile(String path);

    /**
     * 删除存储对象（失败仅记录日志）
     *
     * @param path 对象路径
     */
    void deleteFile(String path);
}
