package com.cloudrive.fileAsyncService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.repositorys.FileInfoMapper;
import com.cloudrive.fileAsyncService.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 文件信息查询服务实现（只读）
 */
@Service
public class FileInfoMapperServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileInfoMapperService {

    @Override
    public FileInfo findByIdAndUserId(Long fileId, Long userId) {
        if (fileId == null || userId == null) {
            return null;
        }
        return lambdaQuery()
                .eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .one();
    }

    @Override
    public List<FileInfo> getSubTree(String localFilePath, Long userId) {
        if (localFilePath == null || userId == null) {
            return Collections.emptyList();
        }
        String matchPath = localFilePath.endsWith("/") ? localFilePath : localFilePath + "/";
        // LIKE 通配符转义：! 作转义符，避免文件名里的 % / _ 被当成通配符
        String escaped = matchPath.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .and(wrapper -> wrapper
                        .eq(FileInfo::getLocalFilePath, localFilePath)
                        .or()
                        .apply("local_file_path LIKE {0} ESCAPE '!'", escaped + "%"))
                .list();
    }
}
