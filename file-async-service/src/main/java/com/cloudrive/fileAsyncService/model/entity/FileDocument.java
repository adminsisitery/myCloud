package com.cloudrive.fileAsyncService.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * ES 文件索引文档（索引 file_index，与单体一致）
 */
@Data
@Document(indexName = "file_index")
public class FileDocument {

    @Id
    private Long id;

    /** 文件名（含后缀） */
    private String fileName;

    /** 文本内容（用于内容检索与向量化） */
    private String content;

    private Long userId;

    private String fileType;
}
