package com.ff.mycloud.handler;

import com.ff.mycloud.ai.retrieval.query.QueryData;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


/**
 * 使用es查询
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
public class ESFileSearchHandler implements FileSearchHandler {

    FileSearchService fileSearchService;
    FileMapper fileMapper;

    public ESFileSearchHandler(FileSearchService fileSearchService,FileMapper fileMapper) {
        this.fileSearchService = fileSearchService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        List<FileInfo> fileInfos = fileSearchService.searchFile(keyword);
        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public List<FileListVO> searchFiles(String keyword, Long userId) {
        List<FileInfo> fileInfos = fileSearchService.searchFile(keyword);
        return fileInfos.stream()
                .filter(f -> f.getUserId().equals(userId))
                .map(fileMapper::toFileListVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FileListVO> searchFiles(QueryData queryData, Long userId) {
        List<FileInfo> files = fileSearchService.searchFileByQD(queryData, userId);
        return files.stream()
                .filter(f -> f.getUserId().equals(userId))
                .map(fileMapper::toFileListVO)
                .collect(Collectors.toList());
    }
}
