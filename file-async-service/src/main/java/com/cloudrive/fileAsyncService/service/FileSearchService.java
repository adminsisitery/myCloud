package com.cloudrive.fileAsyncService.service;

import com.cloudrive.fileAsyncService.model.entity.FileDocument;

/**
 * ES 索引读写服务
 */
public interface FileSearchService {

    /** 写入 / 覆盖文档索引 */
    void save(FileDocument document);

    /** 删除文档索引 */
    void del(Long id);
}
