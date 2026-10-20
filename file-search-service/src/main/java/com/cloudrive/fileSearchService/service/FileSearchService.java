package com.cloudrive.fileSearchService.service;

import com.cloudrive.fileSearchService.model.entity.FileInfo;

import java.util.List;

/**
 * Elasticsearch 文件搜索服务
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public interface FileSearchService {

    /**
     * 从 ES 索引中按文件名/内容关键词搜索当前用户的文件，并回查数据库校验归属
     *
     * @param keyword 关键词
     * @return 匹配的文件列表
     */
    List<FileInfo> searchFile(String keyword);
}
