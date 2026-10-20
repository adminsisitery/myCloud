package com.cloudrive.fileSearchService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.fileSearchService.model.entity.FileInfo;
import com.cloudrive.fileSearchService.repositorys.FileInfoMapper;
import com.cloudrive.fileSearchService.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件信息查询服务实现（只读）
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@Service
public class FileInfoMapperServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileInfoMapperService {

    @Override
    public List<FileInfo> findBykeyWord(String keyword, Long currentUserId) {
        return lambdaQuery()
                .like(FileInfo::getFilename, keyword)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }

    @Override
    public List<FileInfo> findInIdAndUserId(List<Long> ids, Long currentUserId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(FileInfo::getId, ids)
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }
}
