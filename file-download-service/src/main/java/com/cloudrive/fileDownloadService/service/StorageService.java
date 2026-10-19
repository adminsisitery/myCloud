package com.cloudrive.fileDownloadService.service;

import com.aliyun.oss.model.ObjectMetadata;

import java.io.InputStream;

/**
 * 文件存储服务接口（下载服务最小集）
 * <p>
 * 单文件下载使用 {@link #getFileRange(String, long, long)} 做 Range 断点续传，
 * 文件夹打包/压缩包下载还需要整体读取、读取对象元数据（Content-Length）
 * 以及删除临时压缩包。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface StorageService {

    /**
     * 打开完整文件流
     *
     * @param path 存储对象路径
     * @return 文件输入流，调用方负责关闭
     */
    InputStream getFile(String path);

    /**
     * 打开指定区间的文件流
     * <p>start / end 均为闭区间（字节下标），与 OSS Range 语义一致。</p>
     *
     * @param path  存储对象路径
     * @param start 起始字节
     * @param end   结束字节（含）
     * @return 文件输入流，调用方负责关闭
     */
    InputStream getFileRange(String path, long start, long end);

    /**
     * 读取对象元数据（用于下载前获取 Content-Length）
     *
     * @param path 存储对象路径
     * @return 对象元数据
     */
    ObjectMetadata getObjectMetadata(String path);

    /**
     * 删除对象（用于清理过期/取消的临时压缩包）
     *
     * @param path 存储对象路径
     */
    void deleteFile(String path);
}
