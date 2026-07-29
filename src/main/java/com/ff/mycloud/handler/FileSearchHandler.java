package com.ff.mycloud.handler;

import com.ff.mycloud.model.vo.FileListVO;

import java.util.List;

public interface FileSearchHandler {

    /** Controller 使用，内部通过 UserContext 获取 userId */
    List<FileListVO> searchFiles(String keyword);

    /** Tool 专用，显式传入 userId，不依赖 SaToken 上下文 */
    default List<FileListVO> searchFiles(String keyword, Long userId) {
        return searchFiles(keyword);
    }
}
