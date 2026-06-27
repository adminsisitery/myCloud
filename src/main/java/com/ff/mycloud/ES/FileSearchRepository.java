package com.ff.mycloud.ES;

import com.ff.mycloud.model.entity.FileDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Elasticsearch文件索引数据访问接口
 *
 * 基于Spring Data Elasticsearch，继承ElasticsearchRepository提供基础的CRUD操作。
 * 自定义方法通过Spring Data方法命名约定自动生成查询实现。
 * FileDocument的主键类型为Long。
 *
 * @author 飞飞
 * @date 2026/3/14
 */
public interface FileSearchRepository extends ElasticsearchRepository<FileDocument,Long> {

    /**
     * 根据文件名关键词和用户ID模糊搜索文件
     *
     * Spring Data根据方法名自动生成查询：在指定用户的文件索引中，
     * 对fileName字段执行包含匹配（Containing），实现模糊搜索。
     *
     * @param keyword 文件名关键词，支持模糊匹配
     * @param userId  用户ID，用于数据隔离
     * @return 匹配的文件文档列表，无结果时返回空列表
     */
    List<FileDocument> findByFileNameContainingAndUserId(
            String keyword,
            Long userId
    );

    /**
     * 根据文件ID删除ES索引
     *
     * 删除不存在的记录不会报错（no-op），天然幂等。
     *
     * @param fileId 文件ID，对应ES文档的主键
     */
    void deleteById(Long fileId);
}
