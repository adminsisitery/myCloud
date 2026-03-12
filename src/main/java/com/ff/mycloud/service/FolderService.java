package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/11 15:50
 * @version 1.0
 */
 
public interface FolderService {
    Result<String> createFolder(Long parentId, String folderName);

    void downLoadFolder(long fileId, HttpServletResponse response);
}
