package com.cloudrive.fileManageService.handler;

import com.cloudrive.fileManageService.model.vo.FileListVO;

import java.util.List;

/**
 * 文件搜索处理器
 * <p>
 * 搜索策略由配置 {@code file.search} 决定：
 * {@code Mysql} 使用数据库 LIKE（默认，无需 ES）；{@code ES} 使用 Elasticsearch 索引。
 * 两种实现的返回结构与原单体一致。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileSearchHandler {

    /**
     * 搜索当前用户拥有的文件
     *
     * @param keyword 搜索关键词
     * @return 匹配的文件列表
     */
    List<FileListVO> searchFiles(String keyword);
}
