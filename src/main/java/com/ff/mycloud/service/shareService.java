package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.vo.ShareFileVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/5 14:17
 * @version 1.0
 */

public interface shareService {
    void cancelShare(String shareCode);

    /**
     * 创建分享链接
     */
    ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password);

    List<ShareFileVO> getSharedFiles();
}
