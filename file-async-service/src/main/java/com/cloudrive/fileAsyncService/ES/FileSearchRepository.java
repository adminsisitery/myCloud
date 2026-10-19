package com.cloudrive.fileAsyncService.ES;

import com.cloudrive.fileAsyncService.model.entity.FileDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * ES 文件索引仓库（异步服务只写：保存 / 删除）
 */
public interface FileSearchRepository extends ElasticsearchRepository<FileDocument, Long> {
}
