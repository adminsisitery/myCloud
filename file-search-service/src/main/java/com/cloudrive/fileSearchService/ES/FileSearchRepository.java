package com.cloudrive.fileSearchService.ES;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Elasticsearch 文件索引数据访问接口
 * <p>
 * 仅在 {@code file.search=ES} 时被业务代码使用。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
public interface FileSearchRepository extends ElasticsearchRepository<FileDocument, Long> {

    /**
     * 按文件名关键词 + 用户 ID 搜索
     *
     * @param keyword 关键词
     * @param userId  用户 ID
     * @return 匹配的文件文档
     */
    List<FileDocument> findByFileNameContainingAndUserId(String keyword, Long userId);

    /**
     * 按文件内容关键词 + 用户 ID 搜索
     *
     * @param keyword 关键词
     * @param userId  用户 ID
     * @return 匹配的文件文档
     */
    List<FileDocument> findByContentContainingAndUserId(String keyword, Long userId);
}
