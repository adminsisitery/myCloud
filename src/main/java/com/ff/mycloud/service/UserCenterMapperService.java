package com.ff.mycloud.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ff.mycloud.model.entity.UserCenter;

/**
 * 用户中心 Mapper 服务接口
 * <p>提供用户中心表（存储空间相关）的数据库操作。</p>
 *
 * @author 飞飞
 * @date 2026/3/8
 */
public interface UserCenterMapperService extends IService<UserCenter> {

    /**
     * 根据用户ID获取用户中心信息
     *
     * @param currentUserId 用户ID
     * @return 用户中心实体，不存在则返回null
     */
    UserCenter getByUserId(Long currentUserId);

    /**
     * 释放用户已用空间
     *
     * @param currentUserId 用户ID
     * @param size          要释放的空间大小（字节）
     */
    void releaseSpace(Long currentUserId, long size);

    /**
     * 预留用户空间（检查并扣减可用空间）
     *
     * @param userId   用户ID
     * @param fileSize 需要预留的文件大小（字节）
     * @return true表示空间预留成功，false表示空间不足
     */
    boolean reserveSpace(long userId, long fileSize);
}
