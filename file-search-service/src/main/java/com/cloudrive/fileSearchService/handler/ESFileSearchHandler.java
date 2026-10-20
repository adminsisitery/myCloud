package com.cloudrive.fileSearchService.handler;

import com.cloudrive.fileSearchService.mapper.FileMapper;
import com.cloudrive.fileSearchService.model.vo.FileListVO;
import com.cloudrive.fileSearchService.service.FileSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Elasticsearch 搜索策略
 * <p>
 * {@code file.search=ES} 时生效：查询 file_index 索引（文件名/内容），
 * 再回查 t_file_info 完成归属与未删除校验。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
public class ESFileSearchHandler implements FileSearchHandler {

    private final FileSearchService fileSearchService;
    private final FileMapper fileMapper;

    public ESFileSearchHandler(FileSearchService fileSearchService,
                               FileMapper fileMapper) {
        this.fileSearchService = fileSearchService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        return fileSearchService.searchFile(keyword)
                .stream()
                .map(fileMapper::toFileListVO)
                .toList();
    }
}
