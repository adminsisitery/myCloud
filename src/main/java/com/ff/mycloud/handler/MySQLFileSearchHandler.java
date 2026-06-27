package com.ff.mycloud.handler;


import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.mapper.FileMapper;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.model.vo.FileListVO;
import com.ff.mycloud.service.FileInfoMapperService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 使用Mysql Like查询
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "Mysql")
public class MySQLFileSearchHandler implements FileSearchHandler {

    FileMapper fileMapper;
    FileInfoMapperService fileInfoMapperService;

    public MySQLFileSearchHandler(FileMapper fileMapper, FileInfoMapperService fileInfoMapperService) {
        this.fileMapper = fileMapper;
        this.fileInfoMapperService = fileInfoMapperService;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        List<FileInfo> fileInfos = fileInfoMapperService.findBykeyWord(keyword, UserContext.getCurrentUserId());
        return fileInfos.stream().map(fileMapper::toFileListVO).collect(Collectors.toList());
    }
}
