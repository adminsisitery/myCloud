package com.ff.mycloud.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/12 14:09
 * @version 1.0
 */

@Data
@Document(indexName = "file_index")
public class FileDocument {
    @Id
    private Long id;
    private String fileName;
    private Long userId;
    private String fileType;
}
