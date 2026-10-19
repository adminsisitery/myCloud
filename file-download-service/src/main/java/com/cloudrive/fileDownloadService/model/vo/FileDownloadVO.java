package com.cloudrive.fileDownloadService.model.vo;

/**
 * 文件下载元信息
 * <p>
 * 从单体 {@code com.ff.mycloud.model.vo.FileDownloadVO} 迁入。
 * 只承载流式下载所需的元数据（存储路径、下载文件名、Content-Type、文件大小），
 * 真正的文件字节由 {@code FileDownloadService#openRange} 以输入流形式提供。
 * </p>
 *
 * @param path        存储系统中的对象路径
 * @param filename    下载时展示的文件名（含后缀）
 * @param contentType MIME 类型
 * @param size        文件大小（字节）
 * @author 飞飞
 * @date 2026/6/27
 */
public record FileDownloadVO(String path, String filename, String contentType, long size) {
}
