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

    List<FileDocument> findByFileNameContainingAndUserId(String keyword, Long userId, org.springframework.data.domain.Pageable page);

    List<FileDocument> findByContentContainingAndUserId(String keyword, Long userId, org.springframework.data.domain.Pageable page);

    /**
     * 根据文件名关键词和用户ID模糊搜索
     */
    List<FileDocument> findByFileNameContainingAndUserId(
            String keyword,
            Long userId
    );

    /**
     * 根据文件内容搜索
     */
    List<FileDocument> findByContentContainingAndUserId(
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
