package com.cloudrive.fileDownloadService.service;

import java.io.InputStream;

/**
 * 文件存储服务接口（下载服务最小集）
 * <p>
 * 上传服务中的同名接口还包含上传、删除、分片等方法；下载服务只需要按区间打开对象流，
 * 因此这里只保留 {@link #getFileRange(String, long, long)}，避免把上传链路的依赖带进来。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface StorageService {

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
}
