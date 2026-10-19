package com.cloudrive.aiService.service;

import com.cloudrive.aiService.model.entity.UserCenter;

/**
 * 用户空间服务（Agent 工具与动作配额协调）
 * <p>查询走本地只读表；扣减/归还通过 user-service 内网接口，保证空间数据单一写者。</p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
public interface UserCenterMapperService {

    /**
     * 按用户ID查询用户空间信息
     *
     * @param userId 用户ID
     * @return 用户空间实体，不存在返回 null
     */
    UserCenter getByUserId(Long userId);

    /**
     * 预占空间
     *
     * @param userId 用户ID
     * @param size   字节数
     * @return true 表示预占成功
     */
    boolean reserveSpace(long userId, long size);

    /**
     * 释放空间
     *
     * @param userId 用户ID
     * @param size   字节数
     */
    void releaseSpace(Long userId, long size);
}
