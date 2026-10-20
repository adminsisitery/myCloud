package com.cloudrive.fileSearchService.handler;

import com.cloudrive.fileSearchService.model.vo.FileListVO;

import java.util.List;

/**
 * 文件搜索策略接口
 * <p>
 * 具体实现由 {@code file.search} 配置决定：Mysql（默认，数据库 LIKE）或 ES（Elasticsearch 索引）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public interface FileSearchHandler {

    /**
     * 按关键词搜索当前用户的文件
     *
     * @param keyword 关键词
     * @return 匹配的文件列表
     */
    List<FileListVO> searchFiles(String keyword);
}
