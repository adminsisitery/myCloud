package com.ff.mycloud.service;

import com.ff.mycloud.common.result.Result;
import com.ff.mycloud.model.vo.ShareFileVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享服务接口
 * <p>
 * 提供文件分享的核心功能，包括创建分享链接、获取分享文件列表、
 * 取消分享等操作。分享支持设置过期时间和密码保护。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/5 14:17
 * @version 1.0
 */
public interface shareService {

    /**
     * 取消指定分享码对应的分享链接
     * <p>
     * 会同时清理Redis中的延迟队列数据和Token缓存。
     * </p>
     *
     * @param shareCode 分享码，用于唯一标识一个分享记录
     */
    void cancelShare(String shareCode);

    /**
     * 创建文件分享链接
     * <p>
     * 为指定文件生成一个分享链接，可设置过期时间和可选的访问密码。
     * 若密码为空字符串则视为无密码分享。
     * </p>
     *
     * @param fileId     待分享的文件ID
     * @param expireTime 分享过期时间，超过此时间后链接失效
     * @param password   访问密码，可为null或空字符串表示无密码
     * @return 包含分享详情的ShareFileVO对象
     */
    ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password);

    /**
     * 获取当前用户创建的所有有效分享文件列表
     *
     * @return 分享文件VO列表，仅包含未过期的分享记录
     */
    List<ShareFileVO> getSharedFiles();
}
