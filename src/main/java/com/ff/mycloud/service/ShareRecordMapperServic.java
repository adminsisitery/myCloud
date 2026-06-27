package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.ShareRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享记录数据访问服务接口
 * <p>
 * 继承MyBatis-Plus IService，提供ShareRecord的CRUD操作及自定义查询方法。
 * 包括按用户查询未过期分享、按分享码查询、访问计数自增等功能。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/5 14:43
 * @version 1.0
 */
public interface ShareRecordMapperServic extends IService <ShareRecord> {

    /**
     * 查询指定用户所有未过期的分享记录
     *
     * @param id 用户ID
     * @return 未过期的分享记录列表
     */
    public List<ShareRecord> findByUserIdAndNotDel(Long id);

    /**
     * 根据用户ID和分享码查找分享记录
     *
     * @param currentUserId 当前用户ID
     * @param shareCode     分享码
     * @return 匹配的分享记录，未找到返回null
     */
    ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode);

    /**
     * 根据分享码查找分享记录（不限制用户）
     *
     * @param shareCode 分享码
     * @return 匹配的分享记录，未找到返回null
     */
    ShareRecord findByShareCode(String shareCode);

    /**
     * 访问计数加1（原子操作）
     * <p>按当前访问量+1更新，避免并发读写不一致。</p>
     *
     * @param visitCount 当前访问次数
     * @param id         分享记录ID
     */
    void visitedPlus1(long visitCount, long id);

    /**
     * 查询所有已过期但尚未标记为过期的分享记录
     *
     * @param now 当前时间，用于比较过期时间
     * @return 符合条件的分享记录列表
     */
    List<ShareRecord> ExpireTimeBeforeAndIsExpiredFalse(LocalDateTime now);
}
