package com.cloudrive.fileShareService.service;

import com.cloudrive.fileShareService.model.vo.ShareFileVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享服务接口
 * <p>
 * 从原单体 {@code shareService} 迁移：创建分享、分享列表、取消分享、
 * 密码/Token 校验、分享内容下载与过期标记。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface FileShareService {

    /**
     * 创建文件分享链接
     *
     * @param fileId     待分享的文件 ID
     * @param expireTime 分享过期时间
     * @param password   访问密码（null 或空字符串表示无密码）
     * @return 包含分享详情的 VO
     */
    ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password);

    /**
     * 获取当前用户创建的所有未过期分享列表
     *
     * @return 分享文件 VO 列表
     */
    List<ShareFileVO> getSharedFiles();

    /**
     * 取消指定分享码对应的分享链接
     *
     * @param shareCode 分享码
     */
    void cancelShare(String shareCode);

    /**
     * 验证分享 Token 是否有效（无密码分享直接返回 true）
     *
     * @param shareCode 分享码
     * @param token     验证令牌
     * @return true 表示有效
     */
    boolean validateShareToken(String shareCode, String token);

    /**
     * 通过 Token 访问分享文件
     *
     * @param shareCode 分享码
     * @param token     访问令牌
     * @return 分享文件 VO
     */
    ShareFileVO accessShareByToken(String shareCode, String token);

    /**
     * 通过密码访问分享文件（访问成功后访问计数 +1）
     *
     * @param shareCode 分享码
     * @param password  明文密码
     * @return 分享文件 VO
     */
    ShareFileVO accessShare(String shareCode, String password);

    /**
     * 为分享链接生成访问 Token
     *
     * @param shareCode 分享码
     * @param password  明文密码
     * @return 生成的 Token
     */
    String generateShareToken(String shareCode, String password);

    /**
     * 下载分享的文件内容（仅普通文件；文件夹由 FolderService 打包）
     *
     * @param shareCode 分享码
     * @param token     访问令牌（有密码分享需要）
     * @return 文件字节数组
     */
    byte[] downloadSharedFile(String shareCode, String token);

    /**
     * 根据分享码获取对应文件的完整文件名（含后缀）
     *
     * @param shareCode 分享码
     * @return 文件名
     */
    String getFilename(String shareCode);

    /**
     * 批量标记所有已过期的分享为失效状态（定时任务兜底）
     */
    void markExpiredShares();

    /**
     * 单条标记指定 ID 的分享为失效（延迟队列消费者用）
     *
     * @param shareId 分享记录 ID
     * @return true 表示更新成功
     */
    boolean markShareExpired(Long shareId);
}
