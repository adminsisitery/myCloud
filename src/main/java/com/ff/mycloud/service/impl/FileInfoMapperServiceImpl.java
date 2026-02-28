package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.repositorys.FileInfoMapper;
import com.ff.mycloud.service.FileInfoMapperService;
import org.springframework.stereotype.Service;

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

}
