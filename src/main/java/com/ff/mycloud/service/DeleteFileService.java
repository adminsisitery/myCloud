package com.ff.mycloud.service;

import com.ff.mycloud.model.vo.TrashListVO;

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

    /**
     * 彻底删除（物理删除）
     *
     * @param ids 文件ID列表
     */
    void tureDeleteFile(List<Long> ids);

    /**
     * 获取回收站列表
     *
     * @return 回收站文件列表
     */
    List<TrashListVO> getTrashList();

    /**
     * 恢复回收站中的文件/文件夹
     *
     * @param fileId 文件ID
     */
    void restoreFile(Long fileId);

    /**
     * 清空当前用户的回收站（彻底删除全部已删除文件）
     */
    void cleanTrash();
}
