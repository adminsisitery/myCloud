package com.ff.mycloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ff.mycloud.model.entity.ShareRecord;
import com.ff.mycloud.repositorys.ShareRecordMapper;
import com.ff.mycloud.service.ShareRecordMapperServic;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/5 14:42
 * @version 1.0
 */

@Service
public class ShareRecordMapperServicImpl
        extends ServiceImpl<ShareRecordMapper, ShareRecord>
        implements ShareRecordMapperServic {
    @Override
    public List<ShareRecord> findByUserIdAndNotDel(Long id) {
        return lambdaQuery()
                .eq(ShareRecord::getUserId, id)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }

    @Override
    public ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode) {
        return lambdaQuery().eq(ShareRecord::getUserId, currentUserId).eq(ShareRecord::getShareCode, shareCode).one();
    }

    @Override
    public ShareRecord findByShareCode(String shareCode) {
        return lambdaQuery().eq(ShareRecord::getShareCode, shareCode).one();
    }

    @Override
    public void visitedPlus1(long visitCount,long id) {
         lambdaUpdate().set(ShareRecord::getVisitCount, visitCount + 1).eq(ShareRecord::getId,id).update();
    }

    @Override
    public List<ShareRecord> ExpireTimeBeforeAndIsExpiredFalse(LocalDateTime now) {
        return lambdaQuery()
                .lt(ShareRecord::getExpireTime, now)
                .eq(ShareRecord::getIsExpired, false)
                .list();
    }

}
