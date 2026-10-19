package com.cloudrive.aiService.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件服务接口（AI Agent 动作使用）
 * <p>
 * 仅暴露 Agent 动作链路实际使用的方法：保存 AI 生成的文档、重命名、移动、收藏、读取对象内容。
 * 上传/下载/秒传等主流量入口分别由 file-upload-service / file-download-service /
 * file-manage-service 承载，本服务只处理 Agent 的受限写操作。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileService {

    /**
     * 保存 AI 生成的文档（Agent SAVE_DOCUMENT 动作）
     *
     * @param file     文档内容
     * @param parentId 父目录ID，null 或 0 表示根目录
     * @return 存储对象路径，失败返回 null
     */
    String uploadFile(MultipartFile file, Long parentId);

    /**
     * 重命名文件/文件夹
     *
     * @param fileId      文件ID
     * @param newFilename 新名称
     */
    void renameFile(Long fileId, String newFilename);

    /**
     * 移动文件/文件夹
     *
     * @param fileId         文件ID
     * @param targetParentId 目标父目录ID，null 或 0 表示根目录
     */
    void moveFile(Long fileId, Long targetParentId);

    /**
     * 收藏文件
     *
     * @param fileId 文件ID
     */
    void starFile(Long fileId);

    /**
     * 取消收藏
     *
     * @param fileId 文件ID
     */
    void unstarFile(Long fileId);

    /**
     * 按存储路径读取对象流
     *
     * @param path 对象路径
     * @return 输入流（调用方负责关闭）
     */
    InputStream getFile(String path);
}
