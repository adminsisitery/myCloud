package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.FileInfo;

import java.util.List;

public interface FileInfoMapperService extends IService<FileInfo> {
    List<FileInfo> findByHashAndUserId(String hash, Long userId);

    List<FileInfo> findByParentIdAndUserId(Long currentUserId, Long parentId);
}
