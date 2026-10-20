package com.cloudrive.fileSearchService.service.impl;

import com.cloudrive.fileSearchService.ES.FileDocument;
import com.cloudrive.fileSearchService.ES.FileSearchRepository;
import com.cloudrive.fileSearchService.model.entity.FileInfo;
import com.cloudrive.fileSearchService.service.FileInfoMapperService;
import com.cloudrive.fileSearchService.service.FileSearchService;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Elasticsearch 文件搜索服务实现
 * <p>
 * 先按文件名与内容两路检索 ES，合并去重后回查 t_file_info：
 * 只保留属于当前用户且未删除的文件，非文件夹补全后缀。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Service
@ConditionalOnProperty(prefix = "file", name = "search", havingValue = "ES")
public class FileSearchServiceImpl implements FileSearchService {

    private final FileSearchRepository fileSearchRepository;
    private final FileInfoMapperService fileInfoMapperService;

    public FileSearchServiceImpl(FileSearchRepository fileSearchRepository,
                                 FileInfoMapperService fileInfoMapperService) {
        this.fileSearchRepository = fileSearchRepository;
        this.fileInfoMapperService = fileInfoMapperService;
    }

    @Override
    public List<FileInfo> searchFile(String keyword) {
        Long currentUserId = UserContext.getCurrentUserId();

        Set<Long> fileIds = new LinkedHashSet<>();
        for (FileDocument document : fileSearchRepository.findByFileNameContainingAndUserId(keyword, currentUserId)) {
            fileIds.add(document.getId());
        }
        for (FileDocument document : fileSearchRepository.findByContentContainingAndUserId(keyword, currentUserId)) {
            fileIds.add(document.getId());
        }
        if (fileIds.isEmpty()) {
            return List.of();
        }

        List<FileInfo> files = fileInfoMapperService.findInIdAndUserId(List.copyOf(fileIds), currentUserId);
        for (FileInfo file : files) {
            if (!Boolean.TRUE.equals(file.getIsFolder()) && file.getFileSuffix() != null) {
                file.setFilename(file.getFilename() + "." + file.getFileSuffix());
            }
        }
        return files;
    }
}
