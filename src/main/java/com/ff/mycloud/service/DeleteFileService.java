package com.ff.mycloud.service;


import java.util.List;

public interface DeleteFileService {

    /**
     * 批量删除文件 → 回收站
     *
     * @param ids 文件ID列表
     */
    void batchDeleteFile(List<Long> ids);

    /**
     * 删除单文件 → 回收站
     *
     * @param fileId 文件ID
     */
    void deleteSingleFile(Long fileId);
}
