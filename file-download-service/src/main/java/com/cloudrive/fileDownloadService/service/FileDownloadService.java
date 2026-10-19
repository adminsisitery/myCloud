package com.cloudrive.fileDownloadService.service;

import com.cloudrive.fileDownloadService.model.vo.FileDownloadVO;

import java.io.InputStream;

/**
 * 文件下载服务
 * <p>
 * 负责下载前的权限校验与元信息组装，并按 Range 打开存储中的文件流。
 * 控制器只负责 HTTP 头与流式响应，不直接访问 Mapper / 存储实现。
 * </p>
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public interface FileDownloadService {

    /**
     * 校验文件归属并组装下载元信息
     *
     * @param fileId 文件ID
     * @return 下载元信息（存储路径、文件名、Content-Type、大小）
     */
    FileDownloadVO prepareDownload(Long fileId);

    /**
     * 打开指定区间的文件流
     * <p>start / end 均为闭区间（字节下标），与 OSS Range 语义一致。</p>
     *
     * @param path  存储对象路径
     * @param start 起始字节
     * @param end   结束字节（含）
     * @return 文件输入流，调用方负责关闭
     */
    InputStream openRange(String path, long start, long end);
}
