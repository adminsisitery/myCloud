package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/2/28 15:28
 * @version 1.0
 */


@Service
public class FileInfoMapperServiceImpl
        extends ServiceImpl<FileInfoMapper,FileInfo>
        implements FileInfoMapperService {
    //根据hash和用户id查找没有被删除的文件
    @Override
    public List<FileInfo> findByHashAndUserId(String hash, Long userId){
        return lambdaQuery()
                .eq(FileInfo::getSha256Hash, hash)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .list();
    }

    @Override
    public List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId) {

        return lambdaQuery()
                .eq(FileInfo::getUserId, currentUserId)
                .eq(FileInfo::getIsDeleted, false)
                .eq(parentId != null, FileInfo::getParentId, parentId)
                .isNull(parentId == null, FileInfo::getParentId)
                .list();
    }

    @Override
    public boolean UpdateFileNameByFileId(Long fileId, String newFilename, LocalDateTime now) {
        return lambdaUpdate().eq(FileInfo::getId, fileId)
                .set(FileInfo::getFilename, newFilename)
                .set(FileInfo::getOriginalFilename, newFilename)
                .set(FileInfo::getUpdatedAt,now)
                .update();
    }

    @Override
    public Long countFiles(String sha256Hash) {
        return lambdaQuery()
                .eq(FileInfo::getSha256Hash,sha256Hash)
                .eq(FileInfo::getIsDeleted, false)
                .count();
    }

    @Override
    public List<FileInfo> findBykeyWord(String keyword, Long currentUserId) {
        return lambdaQuery()
                .like(FileInfo::getFilename,keyword)
                .eq(FileInfo::getIsDeleted, false)
                .eq(FileInfo::getUserId, currentUserId)
                .list();
    }

    @Override
    public FileInfo findByIdAndUserId(Long fileId, Long userId) {
        return lambdaQuery().eq(FileInfo::getId, fileId)
                .eq(FileInfo::getUserId,userId)
                .eq(FileInfo::getIsDeleted, false)
                .one();
    }


}
