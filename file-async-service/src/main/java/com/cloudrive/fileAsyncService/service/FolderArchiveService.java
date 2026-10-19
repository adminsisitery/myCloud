package com.cloudrive.fileAsyncService.service;

import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * 文件夹 ZIP 打包器（从单体 FolderService.writeFolderArchive 迁入）
 */
public interface FolderArchiveService {

    /**
     * 把整个文件夹写成 ZIP 流
     *
     * @param fileId           文件夹ID
     * @param ownerUserId      所有者用户ID（分享场景为分享者，普通下载为当前用户）
     * @param output           输出流（任务打包场景为临时文件流）
     * @param progressCallback 每写入一段字节的回调，用于更新打包进度
     * @param cancelled        取消检查回调，返回 true 时中断打包
     */
    void writeFolderArchive(long fileId, Long ownerUserId, OutputStream output,
                            LongConsumer progressCallback, BooleanSupplier cancelled);
}
