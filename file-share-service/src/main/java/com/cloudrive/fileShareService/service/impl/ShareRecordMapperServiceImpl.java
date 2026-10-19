package com.cloudrive.fileShareService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.fileShareService.model.entity.ShareRecord;
import com.cloudrive.fileShareService.repositorys.ShareRecordMapper;
import com.cloudrive.fileShareService.service.ShareRecordMapperService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享记录数据访问服务实现类
 * <p>基于 MyBatis-Plus ServiceImpl，用 Lambda 查询操作 t_share_record。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Service
public class ShareRecordMapperServiceImpl
        extends ServiceImpl<ShareRecordMapper, ShareRecord>
        implements ShareRecordMapperService {

    /**
     * 查询指定用户所有未过期的分享记录
     *
     * @param userId 用户 ID
     * @return 未过期的分享记录列表
     */
    @Override
    public List<ShareRecord> findByUserIdAndNotDel(Long userId) {
        return lambdaQuery()
                .eq(ShareRecord::getUserId, userId)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }

    /**
     * 根据用户 ID 和分享码查找分享记录
     *
     * @param currentUserId 当前用户 ID
     * @param shareCode     分享码
     * @return 匹配的分享记录，未找到返回 null
     */
    @Override
    public ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode) {
        return lambdaQuery()
                .eq(ShareRecord::getUserId, currentUserId)
                .eq(ShareRecord::getShareCode, shareCode)
                .one();
    }

    /**
     * 根据分享码查找分享记录（不限制用户）
     *
     * @param shareCode 分享码
     * @return 匹配的分享记录，未找到返回 null
     */
    @Override
    public ShareRecord findByShareCode(String shareCode) {
        return lambdaQuery()
                .eq(ShareRecord::getShareCode, shareCode)
                .one();
    }

    /**
     * 访问计数加 1
     *
     * @param visitCount 当前访问次数
     * @param id         分享记录 ID
     */
    @Override
    public void visitedPlus1(long visitCount, long id) {
        lambdaUpdate()
                .set(ShareRecord::getVisitCount, visitCount + 1)
                .eq(ShareRecord::getId, id)
                .update();
    }

    /**
     * 查询所有已过期但尚未标记为过期的分享记录
     *
     * @param now 当前时间基准
     * @return 符合条件的分享记录列表
     */
    @Override
    public List<ShareRecord> expireTimeBeforeAndNotExpired(LocalDateTime now) {
        return lambdaQuery()
                .lt(ShareRecord::getExpireTime, now)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }
}
