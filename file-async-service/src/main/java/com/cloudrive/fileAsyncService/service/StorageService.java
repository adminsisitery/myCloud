package com.cloudrive.fileAsyncService.service;

import java.io.InputStream;

/**
 * 对象存储只读访问（PG 索引重建时读取原始对象）
 */
public interface StorageService {

    /**
     * 读取对象内容（调用方负责关闭流）
     *
     * @param path 对象路径
     * @return 对象输入流
     */
    InputStream getFile(String path);

    /**
     * 上传对象（文件夹打包 ZIP 写入 OSS）
     *
     * @param objectKey   对象路径
     * @param input       输入流（调用方负责关闭）
     * @param size        对象大小（字节）
     * @param contentType 内容类型
     */
    void uploadObject(String objectKey, InputStream input, long size, String contentType);

    /**
     * 删除对象（清理过期临时压缩包），对象不存在视为成功
     *
     * @param path 对象路径
     */
    void deleteFile(String path);
}
