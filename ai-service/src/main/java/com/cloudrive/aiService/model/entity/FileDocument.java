package com.cloudrive.aiService.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * Elasticsearch 文件索引文档实体
 * <p>
 * 映射到 ES 索引 file_index，用于文件名与内容的全文模糊检索。
 * ES 写入由 file-async-service 的同步消费者维护，ai-service 只读取。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Document(indexName = "file_index")
public class FileDocument {

    /** 文件ID，对应 MySQL 主键，作为 ES 文档 ID */
    @Id
    private Long id;

    /** 文件名（不含后缀），用于模糊搜索 */
    private String fileName;

    /** 文件的纯文本内容 */
    private String content;

    /** 所属用户ID，用于搜索时隔离用户数据 */
    private Long userId;

    /** 文件类型/后缀 */
    private String fileType;
}
