package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.ShareRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/3/5 14:43
 * @version 1.0
 */
 
public interface ShareRecordMapperServic extends IService <ShareRecord> {
       public List<ShareRecord> findByUserIdAndNotDel(Long id);

       ShareRecord findByUserIdAndShareCode(Long currentUserId, String shareCode);

       ShareRecord findByShareCode(String shareCode);

       void visitedPlus1(long visitCount,long id);

       List<ShareRecord> ExpireTimeBeforeAndIsExpiredFalse(LocalDateTime now);
}
