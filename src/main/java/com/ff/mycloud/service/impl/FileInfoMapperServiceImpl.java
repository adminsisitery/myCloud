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
                .orderBy(true,false,FileInfo::getIsFolder)
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

    @Override
    public Boolean existsFolder(Long parentId, String folderName, Long currentUserId) {

        return lambdaQuery()
                .eq(FileInfo::getIsDeleted,false)
                .eq(FileInfo::getParentId,parentId)
                .eq(FileInfo::getIsFolder,true)
                .eq(FileInfo::getFilename,folderName)
                .eq(FileInfo::getUserId,currentUserId)
                .exists();
    }

    @Override
    public Boolean existsParentId(Long parentId, Long currentUserId) {
        return lambdaQuery()
                .eq(FileInfo::getUserId,currentUserId)
                .eq(FileInfo::getIsFolder,true)
                .eq(FileInfo::getId,parentId)
                .exists();

    }

    @Override
    public void getFileTree(Long fileId) {

    }

    @Override
    public List<FileInfo> getSubTree(String localFilePath, Long userId) {
        // 标准化路径匹配：解决路径结尾/导致的匹配失败问题
        String matchPath = localFilePath;
        // 确保拼接后是 "路径/"，而非 "路径//" 或 "路径"
        if (!matchPath.endsWith("/")) {
            matchPath = matchPath + "/";
        }
        final String s = matchPath;
        return lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, false)
                .and(w -> w
                        // 匹配根节点本身（比如文件夹2）
                        .eq(FileInfo::getLocalFilePath, localFilePath)
                        // 匹配所有子节点（比如文件夹2/xxx.txt）
                        .or()
                        .likeRight(FileInfo::getLocalFilePath, s)
                )
                .list();
    }


}
