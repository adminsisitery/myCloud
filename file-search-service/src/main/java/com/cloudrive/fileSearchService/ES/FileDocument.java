package com.cloudrive.fileSearchService.ES;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * Elasticsearch 文件索引文档实体（只读）
 * <p>
 * 映射 ES 索引 {@code file_index}（与原有实现保持一致），
 * 索引由 file-async-service 的 ES 同步消费者维护，本服务只负责查询。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Data
@Document(indexName = "file_index")
public class FileDocument {

    @Id
    private Long id;

    private String fileName;

    private String content;

    private Long userId;

    private String fileType;
}
