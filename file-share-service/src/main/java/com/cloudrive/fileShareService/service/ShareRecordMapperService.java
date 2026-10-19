package com.cloudrive.fileShareService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.fileShareService.model.entity.ShareRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享记录数据访问服务接口
 * <p>
 * 继承 MyBatis-Plus IService，提供 ShareRecord 的 CRUD 及自定义查询：
 * 按用户查未过期分享、按分享码查询、访问计数自增等。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface ShareRecordMapperService extends IService<ShareRecord> {

    /**
     * 查询指定用户所有未过期的分享记录
     *
     * @param userId 用户 ID
     * @return 未过期的分享记录列表
     */
    List<ShareRecord> findByUserIdAndNotDel(Long userId);

    /**
     * 根据用户 ID 和分享码查找分享记录
     *
     * @param currentUserId 当前用户 ID
     * @param shareCode     分享码
     * @return 匹配的分享记录，未找到返回 null
     */
    ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode);

    /**
     * 根据分享码查找分享记录（不限制用户）
     *
     * @param shareCode 分享码
     * @return 匹配的分享记录，未找到返回 null
     */
    ShareRecord findByShareCode(String shareCode);

    /**
     * 访问计数加 1
     *
     * @param visitCount 当前访问次数
     * @param id         分享记录 ID
     */
    void visitedPlus1(long visitCount, long id);

    /**
     * 查询所有已过期但尚未标记为过期的分享记录
     *
     * @param now 当前时间基准
     * @return 符合条件的分享记录列表
     */
    List<ShareRecord> expireTimeBeforeAndNotExpired(LocalDateTime now);
}
