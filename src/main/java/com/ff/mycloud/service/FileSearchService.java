package com.ff.mycloud.service;

import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;

import java.util.List;

/**
 * 文件搜索服务接口
 * <p>
 * 基于Elasticsearch提供文件名模糊搜索功能，同时管理ES索引中文件文档的增删改操作。
 * 搜索范围限定在当前用户拥有的文件中。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public interface FileSearchService {

    /**
     * 根据关键词搜索当前用户的文件
     * <p>在ES中模糊匹配文件名，返回的文件列表会拼接完整文件名（含后缀）。</p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件信息列表
     */
    List<FileInfo> searchFile(String keyword);

    /**
     * 从ES索引中删除指定文件的文档记录
     *
     * @param fileId 文件ID
     */
    void del(Long fileId);

    /**
     * 更新ES索引中文件的名称
     *
     * @param fileId 文件ID
     * @param s      新文件名
     */
    void renameById(Long fileId, String s);

    /**
     * 保存文件文档到ES索引
     *
     * @param fileDocument ES文件文档对象
     */
    void save(FileDocument fileDocument);
}
