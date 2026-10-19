package com.cloudrive.fileAsyncService.service.impl;

import com.cloudrive.fileAsyncService.ES.FileSearchRepository;
import com.cloudrive.fileAsyncService.model.entity.FileDocument;
import com.cloudrive.fileAsyncService.service.FileSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ES 索引读写服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private final FileSearchRepository fileSearchRepository;

    @Override
    public void save(FileDocument document) {
        fileSearchRepository.save(document);
    }

    @Override
    public void del(Long id) {
        fileSearchRepository.deleteById(id);
        log.debug("ES 索引已删除: fileId={}", id);
    }
}
