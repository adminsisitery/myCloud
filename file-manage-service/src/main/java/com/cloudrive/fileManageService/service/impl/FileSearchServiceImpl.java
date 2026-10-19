package com.cloudrive.fileManageService.service.impl;

import com.cloudrive.fileManageService.ES.FileDocument;
import com.cloudrive.fileManageService.ES.FileSearchRepository;
import com.cloudrive.fileManageService.model.entity.FileInfo;
import com.cloudrive.fileManageService.service.FileInfoMapperService;
import com.cloudrive.fileManageService.service.FileSearchService;
import com.cloudrive.pan_common.util.UserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件搜索服务实现：Elasticsearch 策略
 * <p>
 * 从单体 {@code FileSearchServiceImpl#searchFile} 迁入：
 * 按文件名与内容分别查询 ES，合并去重后回查 MySQL（校验用户归属、未删除），
 * 非文件夹补全“文件名.后缀”。索引写入（上传/删除/重命名的 ES 同步）仍由上传链路负责。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
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
        Set<Long> ids = new LinkedHashSet<>();
        fileSearchRepository.findByFileNameContainingAndUserId(keyword, currentUserId)
                .forEach(doc -> ids.add(doc.getId()));
        fileSearchRepository.findByContentContainingAndUserId(keyword, currentUserId)
                .forEach(doc -> ids.add(doc.getId()));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<FileInfo> files = fileInfoMapperService.findInIdAndUserId(new ArrayList<>(ids), currentUserId);
        for (FileInfo file : files) {
            if (!Boolean.TRUE.equals(file.getIsFolder())) {
                file.setFilename(file.getFilename() + "." + file.getFileSuffix());
            }
        }
        return files;
    }
}
