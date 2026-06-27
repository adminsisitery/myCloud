package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.repositorys.ShareRecordMapper;
import com.ff.mycloud.service.ShareRecordMapperServic;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享记录数据访问服务实现类
 * <p>
 * 基于MyBatis-Plus ServiceImpl实现，通过Lambda查询方式操作ShareRecord表。
 * 提供用户维度分享查询、分享码查询、访问计数更新等数据库操作。
 * </p>
 *
 * @author 飞飞
 * @date 2026/3/5 14:42
 * @version 1.0
 */
@Service
public class ShareRecordMapperServicImpl
        extends ServiceImpl<ShareRecordMapper, ShareRecord>
        implements ShareRecordMapperServic {

    /**
     * 查询指定用户所有未过期的分享记录
     *
     * @param id 用户ID
     * @return 未过期的分享记录列表
     */
    @Override
    public List<ShareRecord> findByUserIdAndNotDel(Long id) {
        // 条件：userId匹配 + isExpired为false
        return lambdaQuery()
                .eq(ShareRecord::getUserId, id)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }

    /**
     * 根据用户ID和分享码查询分享记录
     * <p>同时限制用户和分享码，确保用户只能操作自己的分享。</p>
     *
     * @param currentUserId 当前用户ID
     * @param shareCode     分享码
     * @return 匹配的分享记录，未找到返回null
     */
    @Override
    public ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode) {
        return lambdaQuery().eq(ShareRecord::getUserId, currentUserId).eq(ShareRecord::getShareCode, shareCode).one();
    }

    /**
     * 根据分享码查找分享记录
     * <p>不限制用户，用于公开访问场景（如访问者通过分享链接查看文件）。</p>
     *
     * @param shareCode 分享码
     * @return 匹配的分享记录，未找到返回null
     */
    @Override
    public ShareRecord findByShareCode(String shareCode) {
        return lambdaQuery().eq(ShareRecord::getShareCode, shareCode).one();
    }

    /**
     * 访问计数加1
     * <p>使用当前访问次数+1的方式更新，通过WHERE条件确保原子性。</p>
     *
     * @param visitCount 当前访问次数
     * @param id         分享记录ID
     */
    @Override
    public void visitedPlus1(long visitCount, long id) {
         lambdaUpdate().set(ShareRecord::getVisitCount, visitCount + 1).eq(ShareRecord::getId, id).update();
    }

    /**
     * 查询所有过期时间早于指定时间且尚未标记为过期的分享记录
     *
     * @param now 当前时间基准
     * @return 满足条件的分享记录列表
     */
    @Override
    public List<ShareRecord> ExpireTimeBeforeAndIsExpiredFalse(LocalDateTime now) {
        return lambdaQuery()
                .lt(ShareRecord::getExpireTime, now)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }

}
