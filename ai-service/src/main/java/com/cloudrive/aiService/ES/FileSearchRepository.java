package com.cloudrive.aiService.ES;

import com.cloudrive.aiService.model.entity.FileDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * Elasticsearch 文件索引数据访问接口
 * <p>
 * 基于 Spring Data Elasticsearch，自定义方法通过方法命名约定自动生成查询。
 * ES 文档写入由 file-async-service 维护，本服务只读。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileSearchRepository extends ElasticsearchRepository<FileDocument, Long> {

    List<FileDocument> findByFileNameContainingAndUserId(String keyword, Long userId, Pageable page);

    List<FileDocument> findByContentContainingAndUserId(String keyword, Long userId, Pageable page);

    List<FileDocument> findByFileNameContainingAndUserId(String keyword, Long userId);

    List<FileDocument> findByContentContainingAndUserId(String keyword, Long userId);

    void deleteById(Long fileId);
}
