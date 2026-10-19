package com.cloudrive.fileManageService.handler;

import com.cloudrive.fileManageService.mapper.FileMapper;
import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.fileManageService.service.FileSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件搜索处理器：Elasticsearch 实现
 * <p>
 * 仅当 {@code file.search=ES} 时启用，查询 ES 索引 {@code file_index}
 * 并回查 MySQL 校验权限、组装完整文件信息。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
public class ESFileSearchHandler implements FileSearchHandler {

    private final FileSearchService fileSearchService;
    private final FileMapper fileMapper;

    public ESFileSearchHandler(FileSearchService fileSearchService, FileMapper fileMapper) {
        this.fileSearchService = fileSearchService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        return fileSearchService.searchFile(keyword)
                .stream()
                .map(fileMapper::toFileListVO)
                .collect(Collectors.toList());
    }
}
