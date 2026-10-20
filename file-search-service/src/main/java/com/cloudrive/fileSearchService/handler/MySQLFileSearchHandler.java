package com.cloudrive.fileSearchService.handler;

import com.cloudrive.fileSearchService.mapper.FileMapper;
import com.cloudrive.fileSearchService.model.vo.FileListVO;
import com.cloudrive.fileSearchService.service.FileInfoMapperService;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MySQL LIKE 搜索策略（默认）
 * <p>
 * {@code file.search=Mysql}（或未配置）时生效：按文件名 LIKE 查询 t_file_info。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "Mysql", matchIfMissing = true)
public class MySQLFileSearchHandler implements FileSearchHandler {

    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;

    public MySQLFileSearchHandler(FileInfoMapperService fileInfoMapperService,
                                  FileMapper fileMapper) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        Long currentUserId = UserContext.getCurrentUserId();
        return fileInfoMapperService.findBykeyWord(keyword, currentUserId)
                .stream()
                .map(fileMapper::toFileListVO)
                .toList();
    }
}
