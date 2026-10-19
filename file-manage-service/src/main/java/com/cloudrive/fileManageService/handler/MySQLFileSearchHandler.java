package com.cloudrive.fileManageService.handler;

import com.cloudrive.fileManageService.mapper.FileMapper;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.model.vo.FileListVO;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件搜索处理器：MySQL LIKE 实现
 * <p>
 * 默认策略（{@code file.search=Mysql} 或未配置），不依赖 Elasticsearch。
 * 查询语义与原单体 {@code MySQLFileSearchHandler} 一致：按 filename 模糊匹配 + 用户隔离。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "Mysql", matchIfMissing = true)
public class MySQLFileSearchHandler implements FileSearchHandler {

    private final FileInfoMapperService fileInfoMapperService;
    private final FileMapper fileMapper;

    public MySQLFileSearchHandler(FileInfoMapperService fileInfoMapperService, FileMapper fileMapper) {
        this.fileInfoMapperService = fileInfoMapperService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        return fileInfoMapperService.findBykeyWord(keyword, UserContext.getCurrentUserId())
                .stream()
                .map(fileMapper::toFileListVO)
                .collect(Collectors.toList());
    }
}
