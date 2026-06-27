package com.ff.mycloud.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * Elasticsearch 文件索引文档实体
 * <p>
 * 映射到ES索引 file_index，用于文件名的全文模糊搜索。
 * 包含文件ID、文件名、用户ID、文件类型等核心搜索字段。
 * 文档在文件上传/删除/重命名时同步更新。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/12 14:09
 * @version 1.0
 */
@Data
@Document(indexName = "file_index")
public class FileDocument {

    /** 文件ID，对应MySQL主键，作为ES文档ID */
    @Id
    private Long id;

    /** 文件名（不含后缀），用于模糊搜索 */
    private String fileName;

    /** 所属用户ID，用于搜索时隔离用户数据 */
    private Long userId;

    /** 文件类型/后缀 */
    private String fileType;
}
