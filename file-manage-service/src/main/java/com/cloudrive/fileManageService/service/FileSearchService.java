package com.cloudrive.fileManageService.service;

import com.cloudrive.fileManageService.model.entity.FileInfo;

import java.util.List;

/**
 * 文件搜索服务接口（Elasticsearch 实现）
 * <p>
 * 仅在 {@code file.search=ES} 时可用。搜索过程：ES 命中 ID → MySQL 回查校验归属与未删除
 * → 拼接完整文件名，返回可直接展示的文件信息。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileSearchService {

    /**
     * 根据关键词搜索当前用户的文件
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件信息列表，无结果返回空列表
     */
    List<FileInfo> searchFile(String keyword);
}
