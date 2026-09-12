package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * 文件夹服务接口
 * <p>提供文件夹的创建和下载打包功能。</p>
 *
 * @author 飞飞
 * @date 2026/3/11
 * @version 1.0
 */

public interface FolderService {
    /**
     * 创建文件夹
     *
     * @param parentId   父目录ID，为null时在根目录创建
     * @param folderName 文件夹名称
     * @return 操作结果
     */
    Result<String> createFolder(Long parentId, String folderName);

    /**
     * 下载整个文件夹（打包为ZIP）
     * <p>将文件夹及其所有子文件打包成ZIP文件通过HTTP响应输出。</p>
     *
     * @param fileId   文件夹ID
     * @param response HTTP响应对象，用于输出ZIP流
     */
    void downLoadFolder(long fileId, HttpServletResponse response);

    /** 分享场景使用文件所有者查询目录树，不依赖当前登录用户上下文。 */
    void downLoadSharedFolder(long fileId, Long ownerUserId, HttpServletResponse response);

    /** 将指定用户的文件夹写为 ZIP，供 MQ 后台任务复用。 */
    void writeFolderArchive(long fileId, Long ownerUserId, OutputStream output,
                            LongConsumer progressCallback, BooleanSupplier cancelled);
}
